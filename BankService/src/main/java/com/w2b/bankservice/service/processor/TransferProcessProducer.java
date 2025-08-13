package com.w2b.bankservice.service.processor;

import com.w2b.bankservice.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class TransferProcessProducer {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void produce(String message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.BANK_TRANSFER_EXCHANGE,
                RabbitMQConfig.BANK_TRANSFER_ROUTING_KEY, message);
    }
}
