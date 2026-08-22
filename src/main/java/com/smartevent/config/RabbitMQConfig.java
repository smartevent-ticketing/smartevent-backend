package com.smartevent.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 1. Tên Exchange và Routing Keys
    public static final String TOPIC_EXCHANGE = "smartevent.topic.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "smartevent.dlx.exchange";

    public static final String TICKET_ISSUED_QUEUE = "ticket.issued.queue";
    public static final String INVOICE_CREATED_QUEUE = "invoice.created.queue";
    public static final String ORDER_PAID_QUEUE = "order.paid.queue";
    public static final String DEAD_LETTER_QUEUE = "smartevent.dead.letter.queue";

    public static final String ROUTING_TICKET_ISSUED = "event.ticket.issued";
    public static final String ROUTING_INVOICE_CREATED = "event.invoice.created";
    public static final String ROUTING_ORDER_PAID = "event.order.paid";
    public static final String ROUTING_DEAD_LETTER = "event.dead.letter";

    // 2. Định nghĩa Topic Exchange chính & Dead Letter Exchange
    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    // 3. Định nghĩa các Queues (Kèm Dead Letter Exchange để hứng thư lỗi)
    @Bean
    public Queue ticketIssuedQueue() {
        return QueueBuilder.durable(TICKET_ISSUED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue invoiceCreatedQueue() {
        return QueueBuilder.durable(INVOICE_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue orderPaidQueue() {
        return QueueBuilder.durable(ORDER_PAID_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    // 4. Bindings: Nối Queues với Exchange theo Routing Key
    @Bean
    public Binding bindingTicketIssued(Queue ticketIssuedQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(ticketIssuedQueue).to(topicExchange).with(ROUTING_TICKET_ISSUED);
    }

    @Bean
    public Binding bindingInvoiceCreated(Queue invoiceCreatedQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(invoiceCreatedQueue).to(topicExchange).with(ROUTING_INVOICE_CREATED);
    }

    @Bean
    public Binding bindingOrderPaid(Queue orderPaidQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(orderPaidQueue).to(topicExchange).with(ROUTING_ORDER_PAID);
    }

    @Bean
    public Binding bindingDeadLetter(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(ROUTING_DEAD_LETTER);
    }

    // 5. RabbitTemplate
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }
}