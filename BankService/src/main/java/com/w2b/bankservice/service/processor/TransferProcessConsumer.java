package com.w2b.bankservice.service.processor;

import com.rabbitmq.client.Channel;
import com.w2b.bankservice.client.WalletServiceClient;
import com.w2b.bankservice.config.RabbitMQConfig;
import com.w2b.bankservice.domain.FromWalletTransaction;
import com.w2b.bankservice.enums.TransactionStatus;
import com.w2b.bankservice.service.BankTransferService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class TransferProcessConsumer {
    @Autowired
    private BankTransferService bankTransferService;
    @Autowired
    private WalletServiceClient walletServiceClient;

    @RabbitListener(queues = RabbitMQConfig.BANK_TRANSFER_QUEUE)
    public void Consumer(Message message, Channel channel) throws IOException {
        log.debug("Received Message (Transaction Id): {}", message);
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        var transaction = bankTransferService.retrievePendingTransaction(new String(message.getBody()));
        try {
            if (transaction != null) transaction = process(transaction);
            channel.basicAck(deliveryTag, false);
        } catch(Exception e){
            log.error("Error processing Message: {}", e.getMessage());
            log.info("Retrying...");
//            Integer retries = (Integer) message.getMessageProperties().getXDeathHeader().get(0).get("count");
//            if (retries < 3) channel.basicNack(deliveryTag, false, true); // Enqueue for retry upto 3 times
//            else
//            channel.basicReject(deliveryTag, false); // Dead Letter Queue
            transaction = null;
        }
        if (transaction != null) bankTransferService.notifyWalletService(transaction);
    }

    private FromWalletTransaction process(FromWalletTransaction transaction) {
        // Changes the transaction state to PROCESSING
        bankTransferService.updateTransactionStatus(transaction, TransactionStatus.PROCESSING);
        try {
            return bankTransferService.processTransaction(transaction);
        } catch (Exception e) {
            log.error("Error processing Transaction: {}", e.getMessage());
            bankTransferService.updateTransactionStatus(transaction, TransactionStatus.PENDING); // changes status back to PENDING
            throw e;
        }
    }
}
