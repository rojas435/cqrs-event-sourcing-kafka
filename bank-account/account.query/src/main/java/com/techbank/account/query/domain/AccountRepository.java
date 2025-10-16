package com.techbank.account.query.domain;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface AccountRepository extends CrudRepository<BankAccount, String> {
    // allow multiple accounts per holder
    List<BankAccount> findByAccountHolder(String accountHolder);
    List<BankAccount> findByBalanceGreaterThan(double balance);
    List<BankAccount> findByBalanceLessThan(double balance);
}
