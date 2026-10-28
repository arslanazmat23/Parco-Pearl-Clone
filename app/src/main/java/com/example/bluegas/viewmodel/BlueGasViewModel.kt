package com.example.bluegas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Centralized ViewModel acting as the single source of truth for all three surfaces
 * (Customer App, Rider App, Staff Portal) to guarantee cross-surface consistency.
 */
class BlueGasViewModel(
    val store: BlueGasStore = BlueGasStore()
) : ViewModel() {

    init {
        // Stands in for the scheduled hold-expiry job.
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                store.processTimers()
            }
        }
    }

    /**
     * Centralized SharedAppState StateFlow observed by all surfaces.
     */
    val appState: StateFlow<SharedAppState> = store.appState

    fun switchSurface(surface: AppSurface) {
        store.setSurface(surface)
    }

    fun switchStaffSubRole(role: StaffSubRole) {
        store.setStaffRole(role)
    }

    fun switchCustomer(customerId: String) {
        store.setActiveCustomer(customerId)
    }

    fun toggleRiderOffline(isOffline: Boolean) {
        store.setRiderOffline(isOffline)
    }

    fun setRiderOffline(offline: Boolean) {
        store.setRiderOffline(offline)
    }

    fun selectOrderForDevDrawer(orderId: String?) {
        store.selectOrderForDevDrawer(orderId)
    }

    fun markAllNotificationsRead(surface: AppSurface = store.activeSurface, role: StaffSubRole? = null) {
        store.markNotificationsRead(surface, role)
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

    fun replayRiderOutbox(): Pair<Boolean, String> {
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

    fun addAddress(
        customerId: String,
        label: String,
        street: String,
        area: String,
        city: String,
        zoneId: String,
        makeDefault: Boolean
    ): Pair<Boolean, String> {
        return store.addAddress(customerId, label, street, area, city, zoneId, makeDefault)
    }

    fun setDefaultAddress(customerId: String, addressId: String): Pair<Boolean, String> {
        return store.setDefaultAddress(customerId, addressId)
    }

    fun rateOrder(orderId: String, stars: Int, comment: String): Pair<Boolean, String> {
        return store.rateOrder(orderId, stars, comment)
    }

    fun markNotificationRead(notificationId: String): Pair<Boolean, String> {
        return store.markNotificationRead(notificationId)
    }

    fun setActiveCustomer(customerId: String): Pair<Boolean, String> {
        return store.setActiveCustomer(customerId)
    }

    fun raiseSafetyConcern(customerId: String, description: String, orderId: String? = null): Pair<Boolean, String> {
        return store.raiseSafetyConcern(customerId, description, orderId)
    }

    fun notifyPickupArrival(orderId: String): Pair<Boolean, String> {
        return store.notifyPickupArrival(orderId)
    }
}
