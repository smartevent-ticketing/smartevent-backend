package com.smartevent.integration;

import com.smartevent.common.enums.*;
import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.common.util.VNPayUtils;
import com.smartevent.config.VNPayProperties;
import com.smartevent.infrastructure.security.JwtTokenProvider;
import com.smartevent.modules.event.dto.request.CreateEventRequest;
import com.smartevent.modules.event.dto.request.CreateEventSetupRequest;
import com.smartevent.modules.event.dto.request.CompleteDraftSetupRequest;
import com.smartevent.modules.event.dto.request.EventAreaRequest;
import com.smartevent.modules.event.entity.*;
import com.smartevent.modules.event.repository.*;
import com.smartevent.modules.event.dto.response.EventSeatResponse;
import com.smartevent.modules.event.service.EventAccessPolicy;
import com.smartevent.modules.event.service.EventSeatService;
import com.smartevent.modules.event.service.EventSetupService;
import com.smartevent.modules.event.service.EventService;
import com.smartevent.modules.event.service.impl.*;
import com.smartevent.modules.reservation.exception.ReservationException;
import com.smartevent.modules.identity.dto.request.RefreshTokenRequest;
import com.smartevent.modules.identity.entity.*;
import com.smartevent.modules.identity.repository.*;
import com.smartevent.modules.identity.service.AuthService;
import com.smartevent.modules.identity.service.AdminUserService;
import com.smartevent.modules.identity.service.impl.AuthServiceImpl;
import com.smartevent.modules.identity.service.impl.AdminUserServiceImpl;
import com.smartevent.modules.invoice.service.InvoiceService;
import com.smartevent.modules.ordering.dto.request.CreateOrderRequest;
import com.smartevent.modules.ordering.dto.response.OrderResponse;
import com.smartevent.modules.ordering.repository.*;
import com.smartevent.modules.ordering.service.*;
import com.smartevent.modules.ordering.service.impl.*;
import com.smartevent.modules.outbox.service.impl.OutboxServiceImpl;
import com.smartevent.modules.outbox.entity.OutboxEvent;
import com.smartevent.modules.outbox.repository.OutboxEventRepository;
import com.smartevent.modules.payment.service.*;
import com.smartevent.modules.payment.service.impl.*;
import com.smartevent.modules.reservation.dto.request.*;
import com.smartevent.modules.reservation.repository.ReservationRepository;
import com.smartevent.modules.reservation.service.*;
import com.smartevent.modules.reservation.service.impl.*;
import com.smartevent.modules.storage.entity.FileEntity;
import com.smartevent.modules.storage.repository.FileRepository;
import com.smartevent.modules.storage.service.StorageService;
import com.smartevent.modules.ticket.dto.request.*;
import com.smartevent.modules.ticket.dto.response.*;
import com.smartevent.modules.ticket.service.*;
import com.smartevent.modules.ticket.service.impl.*;
import com.smartevent.modules.ticketing.dto.request.TicketTypeRequest;
import com.smartevent.modules.ticketing.dto.request.TicketSalePhaseRequest;
import com.smartevent.modules.ticketing.entity.*;
import com.smartevent.modules.ticketing.repository.*;
import com.smartevent.modules.ticketing.service.impl.*;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL, Flyway, repositories, services and transaction proxies; no external payment/mail calls. */
@Tag("postgres")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackendPostgresTest {
    static PostgreSQLContainer postgres;
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    TransactionTemplate tx;

    @BeforeAll void start() {
        if (System.getenv("BACKEND_TEST_JDBC_URL") == null) {
            postgres = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("backend_review");
            postgres.start();
        }
        try {
            context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        } catch (RuntimeException ex) {
            if (postgres != null) postgres.stop();
            throw ex;
        }
        jdbc = new JdbcTemplate(bean(DataSource.class));
        tx = new TransactionTemplate(bean(PlatformTransactionManager.class));
    }
    @AfterAll void stop() {
        if (context != null) context.close();
        if (postgres != null) postgres.stop();
    }
    @BeforeEach void resetExternalCollaborators() { reset(bean(InvoiceService.class), bean(JwtTokenProvider.class)); }
    <T> T bean(Class<T> type) { return context.getBean(type); }

    record Fixture(UUID event, UUID area, UUID type, UUID phase, User organizer, User buyer) {}

    User user() {
        return bean(UserRepository.class).save(new User(UUID.randomUUID()+"@review.invalid", "unused-test-hash", "Review user", null));
    }

    @Test void adminUserRoleGrantPersistsAndSearchListsRoles() {
        String email = "role-" + UUID.randomUUID() + "@review.invalid";
        UUID userId = tx.execute(status -> {
            User account = new User(email, "unused-test-hash", "Role target", null);
            account.addRole(bean(RoleRepository.class).findByName("CUSTOMER").orElseThrow());
            return bean(UserRepository.class).save(account).getId();
        });

        AdminUserService service = bean(AdminUserService.class);
        var unfiltered = service.listUsers(null, 0, 20);
        assertTrue(unfiltered.totalElements() >= 1);
        assertFalse(unfiltered.content().isEmpty());
        var before = service.listUsers(email, 0, 20);
        assertEquals(1, before.totalElements());
        assertEquals(Set.of("CUSTOMER"), before.content().get(0).roles());

        var granted = service.grantRole(userId, "ORGANIZER", UUID.randomUUID());
        assertEquals(Set.of("CUSTOMER", "ORGANIZER"), granted.roles());
        service.grantRole(userId, "ORGANIZER", UUID.randomUUID());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE ur.user_id = ? AND r.name = 'ORGANIZER'",
                Long.class, userId));
        assertEquals(Set.of("CUSTOMER", "ORGANIZER"),
                bean(UserRepository.class).findByIdWithRoles(userId).orElseThrow().getRoleNames());
    }

    Fixture fixture() {
        return tx.execute(status -> {
            User organizer = user(), buyer = user();
            Venue venue = bean(VenueRepository.class).save(new Venue("Review venue", "Test address", "Test city", null, null, 100));
            Event event = new Event();
            event.setOrganizerId(organizer.getId()); event.setVenueId(venue.getId());
            event.setName("Review event"); event.setSlug(UUID.randomUUID().toString());
            event.setStartTime(Instant.now().plusSeconds(86400)); event.setEndTime(Instant.now().plusSeconds(90000));
            event.setStatus(EventStatus.PUBLISHED);
            event = bean(EventRepository.class).save(event);
            EventArea area = bean(EventAreaRepository.class).save(new EventArea(event.getId(), "Standing", AreaType.STANDING, 100, 0, null));
            TicketType type = bean(TicketTypeRepository.class).save(new TicketType(event.getId(), area.getId(), "Standard", null, "ACTIVE"));
            TicketSalePhase phase = bean(TicketSalePhaseRepository.class).save(new TicketSalePhase(type.getId(), "Sale",
                    new BigDecimal("500000"), 100, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), 4, null, SalePhaseStatus.ACTIVE));
            bean(InventoryCounterRepository.class).save(new InventoryCounter(event.getId(), area.getId(), type.getId(), phase.getId(), 100));
            return new Fixture(event.getId(), area.getId(), type.getId(), phase.getId(), organizer, buyer);
        });
    }

    @Test void closedPhaseKeepsSoldAndHeldCapacityForNewSalePhase() {
        Fixture fixture = fixture();
        tx.executeWithoutResult(status -> {
            TicketSalePhase oldPhase = bean(TicketSalePhaseRepository.class).findById(fixture.phase()).orElseThrow();
            oldPhase.setStatus(SalePhaseStatus.CLOSED);
            bean(TicketSalePhaseRepository.class).save(oldPhase);
            InventoryCounter counter = bean(InventoryCounterRepository.class).findBySalePhaseId(fixture.phase()).orElseThrow();
            counter.setSoldQuantity(3);
            counter.setHeldQuantity(4);
            bean(InventoryCounterRepository.class).save(counter);
        });

        assertEquals(7, bean(TicketSalePhaseRepository.class)
                .sumQuantityByEventAreaIdExcluding(fixture.area(), null));

        Instant start = Instant.now().plusSeconds(60);
        Instant end = Instant.now().plusSeconds(3600);
        var newPhase = bean(TicketSalePhaseServiceImpl.class).createSalePhase(
                fixture.type(), fixture.organizer().getId(), false,
                new TicketSalePhaseRequest("Bổ sung", new BigDecimal("600000"), 93,
                        start, end, 4, null, SalePhaseStatus.DRAFT));
        assertEquals(93, newPhase.quantity());
        assertEquals(100, bean(TicketSalePhaseRepository.class)
                .sumQuantityByEventAreaIdExcluding(fixture.area(), null));
    }

    @Test void publishedSearchFiltersVenueCityCategoryAndPaginates() {
        Fixture first = fixture(), second = fixture();
        UUID categoryId = tx.execute(status -> {
            Category category = bean(CategoryRepository.class).save(
                    new Category("Music", UUID.randomUUID().toString(), null));
            for (Fixture fixture : List.of(first, second)) {
                Event event = bean(EventRepository.class).findById(fixture.event()).orElseThrow();
                Venue venue = bean(VenueRepository.class).findById(event.getVenueId()).orElseThrow();
                venue.setName("Sao Theatre");
                venue.setCity("Ha Noi");
                bean(VenueRepository.class).save(venue);
                bean(EventCategoryRepository.class).save(new EventCategory(event.getId(), category.getId()));
            }
            return category.getId();
        });

        var query = bean(EventQueryService.class);
        var firstPage = query.searchPublishedEvents(org.springframework.data.domain.PageRequest.of(0, 1),
                "sao theatre", "ha noi", categoryId);
        var secondPage = query.searchPublishedEvents(org.springframework.data.domain.PageRequest.of(1, 1),
                "sao theatre", "ha noi", categoryId);
        assertEquals(2, firstPage.totalElements());
        assertEquals(1, firstPage.content().size());
        assertEquals(1, secondPage.content().size());
        assertNotEquals(firstPage.content().get(0).id(), secondPage.content().get(0).id());
        assertEquals(0, query.searchPublishedEvents(org.springframework.data.domain.PageRequest.of(0, 10),
                "sao theatre", "Ho Chi Minh", categoryId).totalElements());
    }

    @Test void publicListingExcludesEndedEventsBeforePagination() {
        String marker = "Timeline " + UUID.randomUUID();
        Instant now = Instant.now();
        List<UUID> ids = tx.execute(status -> {
            User organizer = user();
            Venue venue = bean(VenueRepository.class).save(
                    new Venue("Timeline venue", "Test address", "Test city", null, null, 100));
            List<UUID> created = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                Event event = new Event();
                event.setOrganizerId(organizer.getId());
                event.setVenueId(venue.getId());
                event.setName(marker + " " + index);
                event.setSlug(UUID.randomUUID().toString());
                event.setStatus(EventStatus.PUBLISHED);
                event.setStartTime(index == 0 ? now.minusSeconds(7200)
                        : index == 1 ? now.minusSeconds(1800) : now.plusSeconds(86400));
                event.setEndTime(index == 0 ? now.minusSeconds(3600)
                        : index == 1 ? now.plusSeconds(1800) : now.plusSeconds(90000));
                created.add(bean(EventRepository.class).save(event).getId());
            }
            return created;
        });

        var query = bean(EventQueryService.class);
        var page = org.springframework.data.domain.PageRequest.of(0, 1,
                org.springframework.data.domain.Sort.by("startTime"));
        var firstPage = query.searchPublishedEvents(page, marker, null, null);
        var secondPage = query.searchPublishedEvents(page.next(), marker, null, null);
        assertEquals(2, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertEquals(List.of(ids.get(1)), firstPage.content().stream().map(event -> event.id()).toList());
        assertEquals(List.of(ids.get(2)), secondPage.content().stream().map(event -> event.id()).toList());
    }

    UUID reserve(Fixture f) {
        return bean(ReservationService.class).createReservation(f.buyer().getId(), new CreateReservationRequest(f.event(),
                List.of(new ReservationItemRequest(f.type(), f.phase(), null, 2)), UUID.randomUUID().toString())).id();
    }

    OrderResponse order(Fixture f) {
        return bean(OrderService.class).createOrderFromReservation(f.buyer().getId(), new CreateOrderRequest(reserve(f), null, PaymentMethod.VNPAY));
    }

    Map<String,String> notification(OrderResponse order) {
        var params = new HashMap<String,String>();
        params.put("vnp_TmnCode", "REVIEW"); params.put("vnp_TxnRef", order.orderCode());
        params.put("vnp_Amount", order.totalAmount().movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_TransactionNo", UUID.randomUUID().toString());
        params.put("vnp_ResponseCode", "00"); params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", VNPayUtils.hashAllFields(params, bean(VNPayProperties.class).getHashSecret()));
        return params;
    }

    List<TicketResponse> pay(Fixture f) {
        var order = order(f);
        assertEquals("00", bean(VNPayCallbackHandler.class).handleVNPayIpn(notification(order)).rspCode());
        return bean(TicketService.class).getMyTickets(f.buyer().getId());
    }

    List<Object> parallel(List<Supplier<?>> operations) throws Exception {
        var pool = Executors.newFixedThreadPool(operations.size());
        var barrier = new CyclicBarrier(operations.size());
        try {
            List<Future<Object>> tasks = new ArrayList<>();
            for (Supplier<?> operation : operations) tasks.add(pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { return operation.get(); } catch (BusinessException expectedRaceLoser) { return expectedRaceLoser; }
            }));
            List<Object> results = new ArrayList<>();
            for (Future<Object> task : tasks) results.add(task.get(25, TimeUnit.SECONDS));
            return results;
        } finally { pool.shutdownNow(); }
    }

    CreateEventSetupRequest setupRequest(int standingCapacity) {
        Venue venue = bean(VenueRepository.class).save(new Venue("Setup venue", "Test address", "Test city", null, null, 100));
        Category category = bean(CategoryRepository.class).save(new Category("Setup category", UUID.randomUUID().toString(), null));
        UUID bannerId = bean(FileRepository.class).save(new FileEntity(null, "review-bucket",
                "review/banner-" + UUID.randomUUID() + ".png", "banner.png", "image/png", 100L,
                null, FileVisibility.PUBLIC)).getId();
        return new CreateEventSetupRequest(
                new CreateEventRequest("Setup " + UUID.randomUUID(), null, venue.getId(),
                        Instant.now().plusSeconds(86400), Instant.now().plusSeconds(90000), "Test city",
                        List.of(category.getId()), bannerId, null, false, null, null, false, 50),
                List.of(new CreateEventSetupRequest.Tier("Seated", AreaType.SEATED, new BigDecimal("500000"), 26),
                        new CreateEventSetupRequest.Tier("Standing", AreaType.STANDING, new BigDecimal("200000"), standingCapacity)));
    }

    @Test void eventSetupCommitsMixedTiersAndSubmitsForApproval() {
        User organizer = user();
        var response = bean(EventSetupService.class).createAndSubmit(organizer.getId(), setupRequest(10));

        assertEquals(EventStatus.PENDING_APPROVAL, response.status());
        assertEquals("PENDING_APPROVAL", jdbc.queryForObject("select status from events where id=?", String.class, response.id()));
        var areas = bean(EventAreaRepository.class).findByEventIdOrderBySortOrderAsc(response.id());
        assertEquals(List.of(26, 10), areas.stream().map(EventArea::getCapacity).toList());
        assertEquals(26L, bean(EventSeatRepository.class).countByEventAreaId(areas.get(0).getId()));
        assertEquals(0L, bean(EventSeatRepository.class).countByEventAreaId(areas.get(1).getId()));
        assertEquals(2, bean(TicketTypeRepository.class).findByEventId(response.id()).size());
        assertEquals(2L, jdbc.queryForObject("select count(*) from inventory_counters where event_id=?", Long.class, response.id()));
        assertEquals(36L, jdbc.queryForObject("select sum(total_quantity) from inventory_counters where event_id=?", Long.class, response.id()));
        assertEquals(0L, jdbc.queryForObject("select sum(held_quantity + sold_quantity) from inventory_counters where event_id=?", Long.class, response.id()));
    }

    @Test void eventSetupFailureAfterFirstTierLeavesNoPartialConfiguration() {
        User organizer = user();
        var request = setupRequest(100); // The first tier fits; the second exceeds the venue's total capacity.
        var tables = List.of("events", "event_categories", "event_areas", "event_seats",
                "ticket_types", "ticket_sale_phases", "inventory_counters");
        Map<String, Long> before = new LinkedHashMap<>();
        for (String table : tables) before.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class));

        var failure = assertThrows(BusinessException.class,
                () -> bean(EventSetupService.class).createAndSubmit(organizer.getId(), request));

        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, failure.getErrorCode());
        assertTrue(failure.getMessage().contains("126"));
        for (String table : tables) {
            assertEquals(before.get(table), jdbc.queryForObject("select count(*) from " + table, Long.class),
                    "Partial setup must not remain in " + table);
        }
    }

    @Test void completingDraftCommitsOnceAndRetryDoesNotDuplicateTiers() {
        User organizer = user();
        var request = setupRequest(10);
        UUID eventId = bean(EventService.class).createEvent(organizer.getId(), request.event()).id();
        var completion = new CompleteDraftSetupRequest(request.tiers());

        var submitted = bean(EventSetupService.class).completeDraftAndSubmit(eventId, organizer.getId(), completion);
        var retried = bean(EventSetupService.class).completeDraftAndSubmit(eventId, organizer.getId(), completion);

        assertEquals(EventStatus.PENDING_APPROVAL, submitted.status());
        assertEquals(EventStatus.PENDING_APPROVAL, retried.status());
        assertEquals(2L, bean(EventAreaRepository.class).countByEventId(eventId));
        assertEquals(2L, bean(TicketTypeRepository.class).countByEventId(eventId));
        assertEquals(2L, bean(TicketSalePhaseRepository.class).countByEventId(eventId));
    }

    @Test void failedDraftCompletionRollsBackTiersButKeepsDraftAndBanner() {
        User organizer = user();
        var request = setupRequest(100); // First tier fits; combined capacity exceeds the venue.
        UUID eventId = bean(EventService.class).createEvent(organizer.getId(), request.event()).id();

        assertThrows(BusinessException.class, () -> bean(EventSetupService.class).completeDraftAndSubmit(
                eventId, organizer.getId(), new CompleteDraftSetupRequest(request.tiers())));

        assertEquals("DRAFT", jdbc.queryForObject("select status from events where id=?", String.class, eventId));
        assertEquals(0L, bean(EventAreaRepository.class).countByEventId(eventId));
        assertEquals(0L, bean(TicketTypeRepository.class).countByEventId(eventId));
        assertEquals(0L, bean(TicketSalePhaseRepository.class).countByEventId(eventId));
        assertEquals(1L, bean(EventFileRepository.class).countByEventIdAndFileType(eventId, EventFileType.BANNER));
    }

    @Test void concurrentCheckoutCreatesExactlyOneOrder() throws Exception {
        var f = fixture(); UUID reservation = reserve(f);
        List<Supplier<?>> operations = new ArrayList<>();
        for (int i=0;i<8;i++) operations.add(() -> bean(OrderService.class).createOrderFromReservation(f.buyer().getId(),
                new CreateOrderRequest(reservation, null, PaymentMethod.VNPAY)));
        var results = parallel(operations);
        assertTrue(results.stream().allMatch(OrderResponse.class::isInstance));
        assertEquals(1, results.stream().map(value -> ((OrderResponse)value).id()).distinct().count());
        assertEquals(1L, jdbc.queryForObject("select count(*) from orders where reservation_id=?", Long.class, reservation));
    }

    @Test void unlimitedPhasePaymentUpdatesBalancesAndIssuesTickets() {
        var f=fixture(); var tickets=pay(f);
        assertEquals(2,tickets.size());
        assertEquals(0, jdbc.queryForObject("select held_quantity from user_sale_phase_counters where user_id=? and sale_phase_id=?", Integer.class, f.buyer().getId(), f.phase()));
        assertEquals(2, jdbc.queryForObject("select purchased_quantity from user_sale_phase_counters where user_id=? and sale_phase_id=?", Integer.class, f.buyer().getId(), f.phase()));
        assertEquals(2, jdbc.queryForObject("select sold_quantity from inventory_counters where sale_phase_id=?", Integer.class, f.phase()));
    }

    @Test void concurrentReturnAndIpnFulfilOnlyOnce() throws Exception {
        var f=fixture(); var order=order(f); var params=notification(order);
        parallel(List.of(() -> bean(VNPayCallbackHandler.class).handleVNPayIpn(params),
                () -> bean(VNPayCallbackHandler.class).handleVNPayReturn(params),
                () -> bean(VNPayCallbackHandler.class).handleVNPayIpn(params)));
        assertEquals(2, bean(TicketService.class).getMyTickets(f.buyer().getId()).size());
        assertEquals(1L,jdbc.queryForObject("select count(*) from payments where order_id=?",Long.class,order.id()));
        assertEquals(128, jdbc.queryForObject("select length(payload_hash) from payment_webhook_events where provider_event_id=?",
                Integer.class, order.orderCode()+"_"+params.get("vnp_TransactionNo")));
        verify(bean(InvoiceService.class),times(1)).issueInvoiceForOrder(order.id());
    }

    @Test void transferHasOneWinnerAndRevokesBothOldCredentials() throws Exception {
        var f=fixture(); var ticket=pay(f).get(0); var recipient1=user(); var recipient2=user();
        String oldToken=jdbc.queryForObject("select token_hash from ticket_qr_tokens where ticket_id=? and status='ACTIVE'",String.class,ticket.id());
        var results=parallel(List.of(
                () -> bean(TicketTransferService.class).transferTicket(ticket.id(),f.buyer().getId(),new TransferTicketRequest(recipient1.getEmail(), null)),
                () -> bean(TicketTransferService.class).transferTicket(ticket.id(),f.buyer().getId(),new TransferTicketRequest(recipient2.getEmail(), null))));
        assertEquals(1,results.stream().filter(TicketTransferResponse.class::isInstance).count());
        assertEquals(1L,jdbc.queryForObject("select count(*) from ticket_transfers where ticket_id=?",Long.class,ticket.id()));
        assertEquals(1L,jdbc.queryForObject("select count(*) from ticket_qr_tokens where ticket_id=? and status='ACTIVE'",Long.class,ticket.id()));
        assertEquals(CheckinResult.INVALID,scan(f,ticket.ticketCode()).result());
        assertEquals(CheckinResult.INVALID,scan(f,oldToken).result());
        String newCode=jdbc.queryForObject("select ticket_code from tickets where id=?",String.class,ticket.id());
        assertEquals(CheckinResult.SUCCESS,scan(f,newCode).result());
        assertEquals(CheckinResult.DUPLICATE,scan(f,newCode).result());
    }

    @Test void refreshCannotLeaveFormerOwnerCredentialAfterTransfer() throws Exception {
        var f=fixture(); var ticket=pay(f).get(0); var recipient=user();
        var results=parallel(List.of(() -> bean(TicketService.class).refreshTicketQr(ticket.id(),f.buyer().getId()),
                () -> bean(TicketTransferService.class).transferTicket(ticket.id(),f.buyer().getId(),new TransferTicketRequest(recipient.getEmail(), null))));
        assertEquals(recipient.getId(),jdbc.queryForObject("select current_owner_user_id from tickets where id=?",UUID.class,ticket.id()));
        assertEquals(1L,jdbc.queryForObject("select count(*) from ticket_qr_tokens where ticket_id=? and status='ACTIVE'",Long.class,ticket.id()));
        for(Object result:results) if(result instanceof TicketResponse refreshed) assertEquals(CheckinResult.INVALID,scan(f,refreshed.ticketCode()).result());
    }

    CheckinResponse scan(Fixture f,String code) {
        return bean(CheckinService.class).processCheckin(new CheckinRequest(code,f.event(),"Review gate"),f.organizer().getId());
    }

    @Test void cancellationAndPaymentLeaveNoUsableTicketAndRecordRefund() throws Exception {
        var f=fixture(); var order=order(f); var params=notification(order);
        parallel(List.of(() -> bean(VNPayCallbackHandler.class).handleVNPayIpn(params),
                () -> bean(EventLifecycleService.class).cancelEvent(f.event(),f.organizer().getId(),false,"Review cancellation")));
        assertEquals("CANCELLED",jdbc.queryForObject("select status from events where id=?",String.class,f.event()));
        assertEquals(0L,jdbc.queryForObject("select count(*) from tickets where event_id=? and status='ISSUED'",Long.class,f.event()));
        assertEquals(0,jdbc.queryForObject("select held_quantity from inventory_counters where sale_phase_id=?",Integer.class,f.phase()));
        assertEquals(1L,jdbc.queryForObject("select count(*) from payment_refund_reviews where order_id=?",Long.class,order.id()));
        assertEquals(CheckinResult.INVALID,scan(f,"TCK-UNKNOWN").result());
    }

    @Test void invoiceFailureRollsBackFinancialAndTicketWrites() {
        var f=fixture(); var order=order(f);
        doThrow(new IllegalStateException("simulated invoice failure")).when(bean(InvoiceService.class)).issueInvoiceForOrder(order.id());
        assertThrows(IllegalStateException.class,()->bean(VNPayCallbackHandler.class).handleVNPayIpn(notification(order)));
        assertEquals("PENDING_PAYMENT",jdbc.queryForObject("select status from orders where id=?",String.class,order.id()));
        assertEquals(0L,jdbc.queryForObject("select count(*) from tickets where event_id=?",Long.class,f.event()));
        assertEquals(0L,jdbc.queryForObject("select count(*) from payments where order_id=?",Long.class,order.id()));
        assertEquals(2,jdbc.queryForObject("select held_quantity from inventory_counters where sale_phase_id=?",Integer.class,f.phase()));
        assertEquals(0,jdbc.queryForObject("select sold_quantity from inventory_counters where sale_phase_id=?",Integer.class,f.phase()));
    }

    @Test void reusedRefreshTokenCommitsRevocationOfOtherSessions() {
        User user=user(); String hash=UUID.randomUUID().toString();
        var repository=bean(RefreshTokenRepository.class);
        RefreshToken reused=new RefreshToken(user,hash,Instant.now().plusSeconds(3600)); reused.revoke(); repository.save(reused);
        repository.save(new RefreshToken(user,UUID.randomUUID().toString(),Instant.now().plusSeconds(3600)));
        when(bean(JwtTokenProvider.class).hashToken("review-reused")).thenReturn(hash);
        assertThrows(BusinessException.class,()->bean(AuthService.class).refreshToken(new RefreshTokenRequest("review-reused")));
        assertEquals(0L,jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and revoked_at is null",Long.class,user.getId()));
    }

    @Test void configuredStockPreventsAreaShrinkAndTypeMove() {
        var f=fixture();
        tx.executeWithoutResult(status->{var event=bean(EventRepository.class).findById(f.event()).orElseThrow();event.setStatus(EventStatus.DRAFT);bean(EventRepository.class).save(event);});
        assertThrows(BusinessException.class,()->bean(EventAreaServiceImpl.class).updateArea(f.area(),f.organizer().getId(),false,
                new EventAreaRequest("Standing",AreaType.STANDING,1,0,null)));
        var destination=bean(EventAreaRepository.class).save(new EventArea(f.event(),"Other",AreaType.STANDING,100,1,null));
        assertThrows(BusinessException.class,()->bean(TicketTypeServiceImpl.class).updateTicketType(f.type(),f.organizer().getId(),false,
                new TicketTypeRequest(destination.getId(),"Standard",null,"ACTIVE")));
        assertEquals(100,bean(EventAreaRepository.class).findById(f.area()).orElseThrow().getCapacity());
    }

    @Test void organizerSeesTicketsOwnedByCustomers() {
        var f=fixture();pay(f);
        assertEquals(2,bean(TicketService.class).getTicketsByEvent(f.event(),f.organizer().getId(),false).size());
    }

    @Test void outboxClaimSkipsRowsLockedByAnotherWorker() throws Exception {
        jdbc.update("DELETE FROM outbox_events");
        UUID eventId = tx.execute(status -> bean(OutboxEventRepository.class)
                .save(new OutboxEvent("TEST", UUID.randomUUID(), "ORDER_PAID", "{}")) .getId());
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<OutboxEvent>> first = pool.submit(() -> tx.execute(status -> {
                List<OutboxEvent> rows = bean(OutboxEventRepository.class).lockNextPendingBatch();
                firstLocked.countDown();
                try { assertTrue(releaseFirst.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
                return rows;
            }));
            assertTrue(firstLocked.await(5, TimeUnit.SECONDS));
            Future<List<OutboxEvent>> second = pool.submit(() -> tx.execute(status ->
                    bean(OutboxEventRepository.class).lockNextPendingBatch()));
            assertTrue(second.get(5, TimeUnit.SECONDS).isEmpty());
            releaseFirst.countDown();
            assertEquals(List.of(eventId), first.get(5, TimeUnit.SECONDS).stream().map(OutboxEvent::getId).toList());
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }
    }

    @Test void eventSetupPersistsMaxTicketsPerUser() {
        User organizer = user();
        Venue venue = bean(VenueRepository.class).save(new Venue("Limit venue", "Test address", "Test city", null, null, 100));
        Category category = bean(CategoryRepository.class).save(new Category("Limit category", UUID.randomUUID().toString(), null));
        UUID bannerId = bean(FileRepository.class).save(new FileEntity(null, "review-bucket",
                "review/banner-" + UUID.randomUUID() + ".png", "banner.png", "image/png", 100L,
                null, FileVisibility.PUBLIC)).getId();
        var req = new CreateEventSetupRequest(
                new CreateEventRequest("Setup Limit " + UUID.randomUUID(), null, venue.getId(),
                        Instant.now().plusSeconds(86400), Instant.now().plusSeconds(90000), "Test city",
                        List.of(category.getId()), bannerId, null, false, null, null, false, 50, 4),
                List.of(new CreateEventSetupRequest.Tier("Standing", AreaType.STANDING, new BigDecimal("200000"), 10)));

        var response = bean(EventSetupService.class).createAndSubmit(organizer.getId(), req);
        assertEquals(4, response.maxTicketsPerUser());
        assertEquals(4, jdbc.queryForObject("select max_tickets_per_user from events where id=?", Integer.class, response.id()));
    }

    @Test void eventLevelLimitBlocksExcessTicketsAcrossMultipleTiers() {
        var setup = tx.execute(status -> {
            User organizer = user(), buyer = user();
            Venue venue = bean(VenueRepository.class).save(new Venue("Limit Venue 2", "Address", "City", null, null, 200));
            Event event = new Event();
            event.setOrganizerId(organizer.getId());
            event.setVenueId(venue.getId());
            event.setName("Anti Scalping Event " + UUID.randomUUID());
            event.setSlug(UUID.randomUUID().toString());
            event.setStartTime(Instant.now().plusSeconds(86400));
            event.setEndTime(Instant.now().plusSeconds(90000));
            event.setStatus(EventStatus.PUBLISHED);
            event.setMaxTicketsPerUser(3);
            event = bean(EventRepository.class).save(event);

            EventArea area1 = bean(EventAreaRepository.class).save(new EventArea(event.getId(), "VIP Area", AreaType.STANDING, 50, 0, null));
            TicketType type1 = bean(TicketTypeRepository.class).save(new TicketType(event.getId(), area1.getId(), "VIP", null, "ACTIVE"));
            TicketSalePhase phase1 = bean(TicketSalePhaseRepository.class).save(new TicketSalePhase(type1.getId(), "Phase VIP",
                    new BigDecimal("1000000"), 50, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), 4, null, SalePhaseStatus.ACTIVE));
            bean(InventoryCounterRepository.class).save(new InventoryCounter(event.getId(), area1.getId(), type1.getId(), phase1.getId(), 50));

            EventArea area2 = bean(EventAreaRepository.class).save(new EventArea(event.getId(), "Standard Area", AreaType.STANDING, 50, 1, null));
            TicketType type2 = bean(TicketTypeRepository.class).save(new TicketType(event.getId(), area2.getId(), "Standard", null, "ACTIVE"));
            TicketSalePhase phase2 = bean(TicketSalePhaseRepository.class).save(new TicketSalePhase(type2.getId(), "Phase Standard",
                    new BigDecimal("500000"), 50, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), 4, null, SalePhaseStatus.ACTIVE));
            bean(InventoryCounterRepository.class).save(new InventoryCounter(event.getId(), area2.getId(), type2.getId(), phase2.getId(), 50));

            return new Object[] { event, type1, phase1, type2, phase2, buyer };
        });

        Event event = (Event) setup[0];
        TicketType type1 = (TicketType) setup[1];
        TicketSalePhase phase1 = (TicketSalePhase) setup[2];
        TicketType type2 = (TicketType) setup[3];
        TicketSalePhase phase2 = (TicketSalePhase) setup[4];
        User buyer = (User) setup[5];

        var res1 = bean(ReservationService.class).createReservation(buyer.getId(), new CreateReservationRequest(event.getId(),
                List.of(new ReservationItemRequest(type1.getId(), phase1.getId(), null, 2)), UUID.randomUUID().toString()));
        var order1 = bean(OrderService.class).createOrderFromReservation(buyer.getId(), new CreateOrderRequest(res1.id(), null, PaymentMethod.VNPAY));
        assertEquals("00", bean(VNPayCallbackHandler.class).handleVNPayIpn(notification(order1)).rspCode());
        assertEquals(2, bean(TicketService.class).getMyTickets(buyer.getId()).size());

        var createReservationReq = new CreateReservationRequest(event.getId(),
                List.of(new ReservationItemRequest(type2.getId(), phase2.getId(), null, 2)), UUID.randomUUID().toString());

        ReservationException ex = assertThrows(ReservationException.class, () ->
                bean(ReservationService.class).createReservation(buyer.getId(), createReservationReq));

        assertEquals(ErrorCode.EXCEEDED_TICKET_LIMIT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Bạn đã mua giới hạn số vé cho phép"));
    }

    @Test void concurrentReservationObservesCommittedPurchaseBeforeCheckingEventLimit() throws Exception {
        Fixture f = fixture();
        tx.executeWithoutResult(status -> {
            Event event = bean(EventRepository.class).findById(f.event()).orElseThrow();
            event.setMaxTicketsPerUser(1);
        });

        CountDownLatch purchaseReady = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> tx.executeWithoutResult(status -> {
                var reservation = bean(ReservationService.class).createReservation(f.buyer().getId(),
                        new CreateReservationRequest(f.event(),
                                List.of(new ReservationItemRequest(f.type(), f.phase(), null, 1)),
                                UUID.randomUUID().toString()));
                bean(ReservationRepository.class).flush();
                assertTrue(bean(ReservationService.class).confirmReservation(reservation.id()));
                purchaseReady.countDown();
                try {
                    assertTrue(allowCommit.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(ex);
                }
            }));

            assertTrue(purchaseReady.await(10, TimeUnit.SECONDS));
            Future<Object> second = pool.submit(() -> {
                try {
                    return bean(ReservationService.class).createReservation(f.buyer().getId(),
                            new CreateReservationRequest(f.event(),
                                    List.of(new ReservationItemRequest(f.type(), f.phase(), null, 1)),
                                    UUID.randomUUID().toString()));
                } catch (ReservationException ex) {
                    return ex;
                }
            });

            // The first transaction has moved HELD to PURCHASED but has not committed yet.
            // The second request must wait for its buyer/event lock instead of reading zero.
            Thread.sleep(200);
            assertFalse(second.isDone());
            allowCommit.countDown();
            first.get(10, TimeUnit.SECONDS);

            ReservationException denied = assertInstanceOf(ReservationException.class,
                    second.get(10, TimeUnit.SECONDS));
            assertEquals(ErrorCode.EXCEEDED_TICKET_LIMIT, denied.getErrorCode());
            assertEquals(1, bean(UserSalePhaseCounterRepository.class)
                    .countOccupiedTicketsByUserIdAndEventId(f.buyer().getId(), f.event()));
        } finally {
            allowCommit.countDown();
            pool.shutdownNow();
        }
    }

    @Test void seatMapApiReturnsAllSeatsWithActualStatus() {
        var setup = tx.execute(status -> {
            User organizer = user(), buyer = user();
            Venue venue = bean(VenueRepository.class).save(new Venue("Seatmap Venue", "Address", "City", null, null, 100));
            Event event = new Event();
            event.setOrganizerId(organizer.getId());
            event.setVenueId(venue.getId());
            event.setName("Seatmap Event " + UUID.randomUUID());
            event.setSlug(UUID.randomUUID().toString());
            event.setStartTime(Instant.now().plusSeconds(86400));
            event.setEndTime(Instant.now().plusSeconds(90000));
            event.setStatus(EventStatus.PUBLISHED);
            event = bean(EventRepository.class).save(event);

            EventArea area = bean(EventAreaRepository.class).save(new EventArea(event.getId(), "Seated Area", AreaType.SEATED, 10, 0, null));
            TicketType type = bean(TicketTypeRepository.class).save(new TicketType(event.getId(), area.getId(), "Seated Type", null, "ACTIVE"));
            TicketSalePhase phase = bean(TicketSalePhaseRepository.class).save(new TicketSalePhase(type.getId(), "Phase Seated",
                    new BigDecimal("300000"), 10, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), 4, null, SalePhaseStatus.ACTIVE));
            bean(InventoryCounterRepository.class).save(new InventoryCounter(event.getId(), area.getId(), type.getId(), phase.getId(), 10));

            EventSeat s1 = bean(EventSeatRepository.class).save(new EventSeat(area.getId(), "A", "01", "A-01", SeatStatus.AVAILABLE, null));
            EventSeat s2 = bean(EventSeatRepository.class).save(new EventSeat(area.getId(), "A", "02", "A-02", SeatStatus.AVAILABLE, null));
            EventSeat s3 = bean(EventSeatRepository.class).save(new EventSeat(area.getId(), "A", "03", "A-03", SeatStatus.AVAILABLE, null));

            return new Object[] { event, area, type, phase, buyer, s1, s2, s3 };
        });

        Event event = (Event) setup[0];
        EventArea area = (EventArea) setup[1];
        TicketType type = (TicketType) setup[2];
        TicketSalePhase phase = (TicketSalePhase) setup[3];
        User buyer = (User) setup[4];
        EventSeat s1 = (EventSeat) setup[5];
        EventSeat s2 = (EventSeat) setup[6];
        EventSeat s3 = (EventSeat) setup[7];

        var resHold = bean(ReservationService.class).createReservation(buyer.getId(), new CreateReservationRequest(event.getId(),
                List.of(new ReservationItemRequest(type.getId(), phase.getId(), s2.getId(), 1)), UUID.randomUUID().toString()));
        assertNotNull(resHold);

        User buyer2 = user();
        var resPay = bean(ReservationService.class).createReservation(buyer2.getId(), new CreateReservationRequest(event.getId(),
                List.of(new ReservationItemRequest(type.getId(), phase.getId(), s3.getId(), 1)), UUID.randomUUID().toString()));
        var order = bean(OrderService.class).createOrderFromReservation(buyer2.getId(), new CreateOrderRequest(resPay.id(), null, PaymentMethod.VNPAY));
        assertEquals("00", bean(VNPayCallbackHandler.class).handleVNPayIpn(notification(order)).rspCode());

        List<EventSeatResponse> seatList = bean(EventSeatService.class).getAvailableSeatsByArea(area.getId());
        assertEquals(3, seatList.size());

        Map<String, SeatStatus> statusMap = new HashMap<>();
        seatList.forEach(s -> statusMap.put(s.label(), s.status()));

        assertEquals(SeatStatus.AVAILABLE, statusMap.get("A-01"));
        assertEquals(SeatStatus.HELD, statusMap.get("A-02"));
        assertEquals(SeatStatus.SOLD, statusMap.get("A-03"));
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass=true)
    @EnableJpaRepositories(basePackages="com.smartevent.modules")
    @Import({ReservationItemValidator.class,ReservationResources.class,ReservationQueryService.class,ReservationServiceImpl.class,
            InventoryServiceImpl.class,UserSalePhaseCounterServiceImpl.class,ReservationCheckoutService.class,
            OrderQueryService.class,OrderServiceImpl.class,OrderLifecycleService.class,TicketMutationGuard.class,
            TicketCredentialService.class,TicketTransferServiceImpl.class,CheckinServiceImpl.class,TicketQueryService.class,
            TicketQrService.class,TicketIssuanceService.class,TicketServiceImpl.class,PaymentCompletionService.class,
            VNPayCallbackHandler.class,PaymentReconciliationService.class,EventCancellationHandler.class,TicketCancellationService.class,
            EventConfigurationValidator.class,EventQueryService.class,EventLifecycleService.class,EventAccessPolicy.class,
            EventAreaServiceImpl.class,TicketTypeServiceImpl.class,AuthServiceImpl.class,OutboxServiceImpl.class,
            EventSetupServiceImpl.class,EventServiceImpl.class,EventCommandService.class,EventSeatServiceImpl.class,
            TicketSalePhaseServiceImpl.class,AdminUserServiceImpl.class})
    static class TestConfiguration {
        @Bean DataSource dataSource() {
            if (postgres != null)
                return new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            String url=System.getenv("BACKEND_TEST_JDBC_URL");
            boolean isolatedLocal=url != null
                    && url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/backend_review");
            if(!isolatedLocal)
                throw new IllegalArgumentException("Use Testcontainers or a dedicated loopback backend_review database");
            String username=System.getenv().getOrDefault("BACKEND_TEST_DB_USERNAME","postgres");
            String password=System.getenv().getOrDefault("BACKEND_TEST_DB_PASSWORD","");
            return new DriverManagerDataSource(url,username,password);
        }
        @Bean(initMethod="migrate") Flyway flyway(DataSource source) {
            return Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        }
        @Bean @DependsOn("flyway") LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory=new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source); factory.setPackagesToScan("com.smartevent.modules");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.jdbc.time_zone","UTC"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean InvoiceService invoiceService() { return mock(InvoiceService.class); }
        @Bean JwtTokenProvider jwtTokenProvider() { return mock(JwtTokenProvider.class); }
        @Bean PasswordEncoder passwordEncoder() { return mock(PasswordEncoder.class); }
        @Bean StorageService storageService() { return mock(StorageService.class); }
        @Bean VNPayProperties vnPayProperties() {
            var props=new VNPayProperties(); props.setTmnCode("REVIEW");props.setHashSecret("review-only-signing-key");return props;
        }
    }
}
