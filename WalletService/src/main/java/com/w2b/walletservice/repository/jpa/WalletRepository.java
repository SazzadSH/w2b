package com.w2b.walletservice.repository.jpa;

import com.w2b.walletservice.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByWalletId(@Param("walletId") String walletId);
}
