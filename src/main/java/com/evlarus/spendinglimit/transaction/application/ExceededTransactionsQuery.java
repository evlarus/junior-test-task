package com.evlarus.spendinglimit.transaction.application;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import java.util.List;

public interface ExceededTransactionsQuery {

    List<ExceededTransaction> findByAccount(AccountNumber account, int page, int size);
}
