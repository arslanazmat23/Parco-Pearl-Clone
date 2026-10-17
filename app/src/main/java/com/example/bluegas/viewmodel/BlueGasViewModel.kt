package com.example.bluegas.viewmodel

import androidx.lifecycle.ViewModel
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.data.SharedAppState
import com.example.bluegas.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Centralized ViewModel acting as the single source of truth for all three surfaces
 * (Customer App, Rider App, Staff Portal) to guarantee cross-surface consistency.
 */
class BlueGasViewModel(
    val store: BlueGasStore = BlueGasStore()
) : ViewModel() {

    /**
     * Centralized SharedAppState StateFlow observed by all surfaces.
     */
    val appState: StateFlow<SharedAppState> = store.appState

    fun switchSurface(surface: AppSurface) {
        store.activeSurface = surface
        store.notifyChanged()
    }

    fun switchStaffSubRole(role: StaffSubRole) {
        store.activeStaffSubRole = role
        store.notifyChanged()
    }

    fun switchCustomer(customerId: String) {
        store.activeCustomerId = customerId
        store.notifyChanged()
    }

    fun toggleRiderOffline(isOffline: Boolean) {
        store.isRiderOffline = isOffline
        store.notifyChanged()
    }

    fun selectOrderForDevDrawer(orderId: String?) {
        store.selectedOrderIdForDrawer = orderId
        store.notifyChanged()
    }

    fun markAllNotificationsRead() {
        store.notifications.forEach { it.isRead = true }
        store.notifyChanged()
    }

    fun resetDemoData() {
        store.seedInitialData()
    }

    fun createOrder(
        customerId: String,
        addressId: String,
        fulfilmentMode: FulfilmentMode,
        sku: String,
        quantity: Int,
        includeDeposit: Boolean,
        paymentMethod: PaymentMethod
    ): Pair<Order?, String?> {
        return store.createOrder(customerId, addressId, fulfilmentMode, sku, quantity, includeDeposit, paymentMethod)
    }

    fun confirmPaymentAndAdvance(
        orderId: String,
        forcedOutcome: String = "SUCCESS"
    ): Pair<Boolean, String> {
        return store.confirmPaymentAndAdvance(orderId, forcedOutcome)
    }

    fun distributorAcceptOrder(orderId: String): Pair<Boolean, String> {
        return store.distributorAcceptOrder(orderId)
    }

    fun allocateCylinderSerial(orderId: String, serialNumber: String): Pair<Boolean, String> {
        return store.allocateCylinderSerial(orderId, serialNumber)
    }

    fun dispatchOrReadyForPickup(orderId: String, riderId: String? = null): Pair<Boolean, String> {
        return store.dispatchOrReadyForPickup(orderId, riderId)
    }

    fun arriveAtHandover(orderId: String): Pair<Boolean, String> {
        return store.arriveAtHandover(orderId)
    }

    fun replayRiderOutbox(): String {
        return store.replayRiderOutbox()
    }

    fun simulateSerialConflict(): String {
        return store.simulateSerialConflict()
    }

    fun raiseFulfilmentException(
        orderId: String,
        reason: ExceptionReason,
        details: String
    ): Pair<Boolean, String> {
        return store.raiseFulfilmentException(orderId, reason, details)
    }

    fun recoverFulfilmentException(orderId: String, targetStatus: OrderStatus): Pair<Boolean, String> {
        return store.recoverFulfilmentException(orderId, targetStatus)
    }

    fun raiseComplaint(orderId: String, issueType: String, description: String): Pair<Boolean, String> {
        return store.raiseComplaint(orderId, issueType, description)
    }

    fun executeRefund(orderId: String, amountPaisa: Long, reason: String): Pair<Boolean, String> {
        return store.executeRefund(orderId, amountPaisa, reason)
    }

    fun cancelOrder(orderId: String, reason: String = "Cancelled by Customer"): Pair<Boolean, String> {
        return store.cancelOrder(orderId, reason)
    }

    fun approvePartnerApplication(appId: String, actor: String = "Operations Lead"): Pair<Boolean, String> {
        return store.approvePartnerApplication(appId, actor)
    }

    fun rejectPartnerApplication(appId: String, reason: String, actor: String = "Operations Lead"): Pair<Boolean, String> {
        return store.rejectPartnerApplication(appId, reason, actor)
    }

    fun resolveSupportCase(caseId: String, notes: String, actor: String = "Support Lead"): Pair<Boolean, String> {
        return store.resolveSupportCase(caseId, notes, actor)
    }
}
