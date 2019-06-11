package net.corda.accounts.workflows

import net.corda.accounts.contracts.schemas.PersistentAccountInfo
import net.corda.accounts.contracts.states.AccountInfo
import net.corda.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.keys.PublicKeyHashToExternalId
import net.corda.node.services.vault.VaultSchemaV1
import java.util.*

val FlowLogic<*>.accountService get() = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)

val ServiceHub.ourIdentity get() = myInfo.legalIdentities.first()

// Query utilities.

/** Returns the base accountInfo info query criteria. */
val accountBaseCriteria = QueryCriteria.VaultQueryCriteria(
        contractStateTypes = setOf(AccountInfo::class.java),
        status = Vault.StateStatus.UNCONSUMED
)

/** To query [AccountInfo]s by host. */
fun accountHostCriteria(host: Party): QueryCriteria {
    return builder {
        val partySelector = PersistentAccountInfo::host.equal(host)
        QueryCriteria.VaultCustomQueryCriteria(partySelector)
    }
}

/** To query [AccountInfo]s by name. */
fun accountNameCriteria(name: String): QueryCriteria {
    return builder {
        val nameSelector = PersistentAccountInfo::name.equal(name)
        QueryCriteria.VaultCustomQueryCriteria(nameSelector)
    }
}

/** To query [AccountInfo]s by id. */
fun accountUUIDCriteria(id: UUID): QueryCriteria {
    return builder {
        val idSelector = PersistentAccountInfo::id.equal(id)
        QueryCriteria.VaultCustomQueryCriteria(idSelector)
    }
}

/** To query [ContractState]s by which account the participant keys are linked to. */
fun externalIdCriteria(accountIds: List<UUID>): QueryCriteria {
    return builder {
        val externalIdSelector = VaultSchemaV1.StateToExternalId::externalId.`in`(accountIds)
        QueryCriteria.VaultCustomQueryCriteria(externalIdSelector)
    }
}

// For writing less messy HQL.

/** Table names. */

val publicKeyHashToExternalId = PublicKeyHashToExternalId::class.java.name
val persistentKey = BasicHSMKeyManagementService.PersistentKey::class.java.name

/** Column names. */

val publicKeyHashToExternalId_externalId = PublicKeyHashToExternalId::externalId.name
val publicKeyHashToExternalId_publicKeyHash = PublicKeyHashToExternalId::publicKeyHash.name
val persistentKey_publicKeyHash = BasicHSMKeyManagementService.PersistentKey::publicKeyHash.name
val persistentKey_publicKey = BasicHSMKeyManagementService.PersistentKey::publicKey.name