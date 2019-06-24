package net.corda.gold.trading

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
class SplitLoanFlow(
        private val oldLoanBook: StateAndRef<LoanBook>,
        private val amountToSplitOff: Long,
        private val carbonCopyReceivers: List<AccountInfo>?
) : FlowLogic<List<StateAndRef<LoanBook>>>() {

    constructor(oldLoanBook: StateAndRef<LoanBook>, amountToSplitOff: Long) : this(oldLoanBook, amountToSplitOff, null)


    @Suspendable
    override fun call(): List<StateAndRef<LoanBook>> {


        if (oldLoanBook.state.data.owningAccount == null) {
            throw IllegalStateException("Can only split a loan that is already owned")
        }

        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        val account =
                accountService.accountInfo(oldLoanBook.state.data.owningAccount!!)
                        ?: throw IllegalStateException("Attempting to split a loan owned by an account we do not know about")

        if (account.state.data.accountHost != serviceHub.myInfo.legalIdentities.first()) {
            throw IllegalStateException("Attempting to split a loan owned by an account we do not host")
        }

        if (amountToSplitOff >= oldLoanBook.state.data.valueInUSD) {
            throw IllegalArgumentException("Cannot split off more than existing loan value")
        }

        val newLoanBook = oldLoanBook.state.data.copy(valueInUSD = amountToSplitOff)
        val oldLoanBookWithAmountReduced = oldLoanBook.state.data.copy(valueInUSD = oldLoanBook.state.data.valueInUSD - amountToSplitOff)

        val txBuilder = TransactionBuilder(notary = oldLoanBook.state.notary)
                .addInputState(oldLoanBook)
                .addOutputState(newLoanBook)
                .addOutputState(oldLoanBookWithAmountReduced)
                .addCommand(LoanBookContract.SPLIT, oldLoanBook.state.data.owningAccount!!, account.state.data.accountHost.owningKey)
                .addReferenceState(ReferencedStateAndRef(account))

        //sign with our node key AND the private key which corresponds with this account - it must be in our kms as we are the hosts of the account
        val keysToSignWith =
                listOfNotNull(oldLoanBook.state.data.owningAccount, serviceHub.myInfo.legalIdentities.first().owningKey)

        val locallySignedTx = serviceHub.signInitialTransaction(txBuilder, keysToSignWith)

        val notarisedTransaction = subFlow(FinalityFlow(locallySignedTx, emptyList()))

        val splitLoans = notarisedTransaction.coreTransaction.outRefsOfType<LoanBook>()

        val accountsToBroadCastTo = carbonCopyReceivers
                ?: subFlow(GetAllInterestedAccountsFlow(account.state.data.accountId))

        accountsToBroadCastTo.forEach { accountToNotify ->
            splitLoans.forEach { loanStateToShare ->
                subFlow(ShareStateWithAccountFlow(accountToNotify, loanStateToShare))
            }
        }

        return splitLoans
    }

}