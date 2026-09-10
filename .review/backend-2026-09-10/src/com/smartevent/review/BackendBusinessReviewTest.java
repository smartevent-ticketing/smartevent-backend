package com.smartevent.review;

import com.smartevent.common.enums.*;
import com.smartevent.common.error.BusinessException;
import com.smartevent.common.util.VNPayUtils;
import com.smartevent.config.VNPayProperties;
import com.smartevent.infrastructure.security.JwtTokenProvider;
import com.smartevent.modules.event.entity.*;
import com.smartevent.modules.event.repository.*;
import com.smartevent.modules.identity.dto.request.RefreshTokenRequest;
import com.smartevent.modules.identity.entity.RefreshToken;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.repository.*;
import com.smartevent.modules.identity.service.AuthService;
import com.smartevent.modules.identity.service.impl.AuthServiceImpl;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.service.OrderLifecycleService;
import com.smartevent.modules.payment.repository.*;
import com.smartevent.modules.payment.entity.Payment;
import com.smartevent.modules.payment.service.impl.*;
import com.smartevent.modules.reservation.dto.request.*;
import com.smartevent.modules.reservation.entity.*;
import com.smartevent.modules.reservation.exception.ReservationException;
import com.smartevent.modules.reservation.repository.*;
import com.smartevent.modules.reservation.service.impl.*;
import com.smartevent.modules.ticket.dto.request.CheckinRequest;
import com.smartevent.modules.ticket.entity.*;
import com.smartevent.modules.ticket.repository.*;
import com.smartevent.modules.ticket.service.impl.CheckinServiceImpl;
import com.smartevent.modules.ticketing.entity.*;
import com.smartevent.modules.ticketing.repository.*;
import com.smartevent.modules.ticketing.service.*;
import com.smartevent.modules.ticketing.service.impl.UserSalePhaseCounterServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Deliberately separate from the normal suite; expectations are the required business behavior. */
class BackendBusinessReviewTest {
    @Test
    void gatewayLinkMustNotOutliveOrderPaymentDeadline() {
        var properties = mock(VNPayProperties.class);
        when(properties.getHashSecret()).thenReturn("isolated-review-test-secret");
        when(properties.getVersion()).thenReturn("2.1.0");
        when(properties.getCommand()).thenReturn("pay");
        when(properties.getTmnCode()).thenReturn("REVIEW");
        when(properties.getReturnUrl()).thenReturn("https://review.invalid/return");
        when(properties.getPayUrl()).thenReturn("https://review.invalid/pay");
        var order = new Order(); order.setOrderCode("ORD-REVIEW");
        order.setPaymentDeadline(Instant.now().plusSeconds(30));
        var payment = new Payment(); payment.setAmount(new BigDecimal("500000"));
        String url = new VNPayGatewayProvider(properties).createPaymentUrl(payment, order,
                mock(jakarta.servlet.http.HttpServletRequest.class), null);
        String expiryValue = Arrays.stream(URI.create(url).getRawQuery().split("&"))
                .filter(part -> part.startsWith("vnp_ExpireDate="))
                .map(part -> URLDecoder.decode(part.substring(part.indexOf('=') + 1), StandardCharsets.UTF_8))
                .findFirst().orElseThrow();
        Instant gatewayExpiry = LocalDateTime.parse(expiryValue, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                .atZone(ZoneId.systemDefault()).toInstant();
        assertFalse(gatewayExpiry.isAfter(order.getPaymentDeadline()),
                "Gateway must stop accepting payment when the reserved tickets expire");
    }

    @Test
    void unlimitedSalePhaseMustCompletePurchaseAfterSuccessfulHold() {
        var repository = mock(UserSalePhaseCounterRepository.class);
        var held = new AtomicInteger();
        when(repository.atomicHoldUserQuantity(any(), any(), anyInt(), anyInt(), any()))
                .thenAnswer(call -> { held.addAndGet(call.getArgument(2)); return 1; });
        when(repository.atomicConfirmUserPurchase(any(), any(), anyInt(), any()))
                .thenAnswer(call -> held.get() >= (int) call.getArgument(2) ? 1 : 0);
        var service = new UserSalePhaseCounterServiceImpl(repository);
        UUID user = UUID.randomUUID(), phase = UUID.randomUUID();
        service.holdUserTickets(user, phase, 2, null);
        assertDoesNotThrow(() -> service.confirmUserPurchase(user, phase, 2),
                "A valid unlimited hold must not fail when the payment is confirmed");
    }

    @Test
    void splittingItemsMustNotBypassFourTicketsPerOrder() {
        var reservations = mock(ReservationRepository.class);
        var items = mock(ReservationItemRepository.class);
        var events = mock(EventRepository.class);
        var areas = mock(EventAreaRepository.class);
        var seats = mock(EventSeatRepository.class);
        var types = mock(TicketTypeRepository.class);
        var phases = mock(TicketSalePhaseRepository.class);
        UUID eventId = UUID.randomUUID(), areaId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID(), phaseId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId); event.setName("Review event"); event.setStatus(EventStatus.PUBLISHED);
        EventArea area = new EventArea();
        area.setId(areaId); area.setEventId(eventId); area.setAreaType(AreaType.STANDING);
        TicketType type = new TicketType(eventId, areaId, "Standard", null, "ACTIVE");
        type.setId(typeId);
        TicketSalePhase phase = new TicketSalePhase(typeId, "Sale", BigDecimal.TEN, 100,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), 4, 20, SalePhaseStatus.ACTIVE);
        phase.setId(phaseId);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(areas.findById(areaId)).thenReturn(Optional.of(area));
        when(types.findById(typeId)).thenReturn(Optional.of(type));
        when(phases.findById(phaseId)).thenReturn(Optional.of(phase));
        when(reservations.save(any(Reservation.class))).thenAnswer(call -> {
            Reservation r = call.getArgument(0); r.setId(UUID.randomUUID()); return r;
        });
        when(items.save(any(ReservationItem.class))).thenAnswer(call -> {
            ReservationItem item = call.getArgument(0); item.setId(UUID.randomUUID()); return item;
        });
        var service = new ReservationServiceImpl(new ReservationItemValidator(areas, seats, types, phases),
                new ReservationResources(items, seats, mock(InventoryService.class), mock(UserSalePhaseCounterService.class)),
                mock(ReservationQueryService.class), reservations, items, events);
        var request = new CreateReservationRequest(eventId, List.of(
                new ReservationItemRequest(typeId, phaseId, null, 3),
                new ReservationItemRequest(typeId, phaseId, null, 3)), UUID.randomUUID().toString());
        assertThrows(ReservationException.class, () -> service.createReservation(UUID.randomUUID(), request),
                "Two lines of 3 tickets exceed the phase limit of 4 per order");
    }

    @Test
    void cancelledEventMustRejectGateCheckin() {
        var tickets = mock(TicketRepository.class);
        var events = mock(EventRepository.class);
        var checkins = mock(TicketCheckinRepository.class);
        UUID eventId = UUID.randomUUID(), ticketId = UUID.randomUUID();
        Event event = new Event(); event.setId(eventId); event.setStatus(EventStatus.CANCELLED);
        Ticket ticket = new Ticket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                eventId, null, null, null, null, "TCK-REVIEW-123");
        ticket.setId(ticketId);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(tickets.findByTicketCode(ticket.getTicketCode())).thenReturn(Optional.of(ticket));
        when(tickets.markTicketAsUsedAtomic(eq(ticketId), any())).thenReturn(1);
        when(checkins.save(any())).thenAnswer(call -> {
            TicketCheckin checkin = call.getArgument(0); checkin.setId(UUID.randomUUID()); return checkin;
        });
        var service = new CheckinServiceImpl(tickets, mock(TicketQrTokenRepository.class), checkins,
                mock(UserRepository.class), mock(EventSeatRepository.class), mock(TicketTypeRepository.class), events);
        var response = service.processCheckin(new CheckinRequest(ticket.getTicketCode(), eventId, "Gate"), UUID.randomUUID());
        assertNotEquals(CheckinResult.SUCCESS, response.result(), "Cancelled events must not admit attendees");
    }

    @Test
    void signedReturnWithWrongAmountMustNotCompleteOrder() {
        var payments = mock(PaymentRepository.class);
        var lifecycle = mock(OrderLifecycleService.class);
        var completion = mock(PaymentCompletionService.class);
        var properties = mock(VNPayProperties.class);
        String testSecret = "isolated-review-test-secret";
        when(properties.getHashSecret()).thenReturn(testSecret);
        Order order = new Order();
        order.setId(UUID.randomUUID()); order.setOrderCode("ORD-REVIEW");
        order.setStatus(OrderStatus.PENDING_PAYMENT); order.setTotalAmount(new BigDecimal("500000"));
        when(lifecycle.lockForPayment(order.getOrderCode())).thenReturn(Optional.of(order));
        var params = new HashMap<String, String>();
        params.put("vnp_TxnRef", order.getOrderCode()); params.put("vnp_TransactionNo", "1234");
        params.put("vnp_ResponseCode", "00"); params.put("vnp_Amount", "10000");
        params.put("vnp_SecureHash", VNPayUtils.hashAllFields(params, testSecret));
        var handler = new VNPayCallbackHandler(payments, mock(PaymentWebhookEventRepository.class), lifecycle,
                properties, new ObjectMapper(), completion);
        handler.handleVNPayReturn(params);
        verify(completion, never()).complete(any(), any(), any());
    }

    @Test
    void detectingRefreshTokenReuseMustPersistRevocationDespiteRejectedRequest() {
        var refreshTokens = mock(RefreshTokenRepository.class);
        var jwt = mock(JwtTokenProvider.class);
        User user = new User(); user.setId(UUID.randomUUID());
        RefreshToken reused = new RefreshToken(user, "review-hash", Instant.now().plusSeconds(3600));
        reused.revoke();
        when(jwt.hashToken("review-token")).thenReturn("review-hash");
        when(refreshTokens.findByTokenHashWithUser("review-hash")).thenReturn(Optional.of(reused));
        var service = new AuthServiceImpl(mock(UserRepository.class), mock(RoleRepository.class), jwt,
                refreshTokens, mock(PasswordEncoder.class));
        var transactionManager = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction);
        var proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        AuthService proxy = (AuthService) proxyFactory.getProxy();
        assertThrows(BusinessException.class, () -> proxy.refreshToken(new RefreshTokenRequest("review-token")));
        verify(refreshTokens).revokeAllUserTokens(eq(user.getId()), any());
        verify(transactionManager, never()).rollback(transaction);
    }
}
