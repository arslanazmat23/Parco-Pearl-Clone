package com.example.bluegas.model

/**
 * Centralized i18n map for Blue Gas.
 * All user-facing strings are looked up by key so that an Urdu catalogue can be added easily.
 */
object I18n {
    var currentLanguage: String = "en"

    private val enStrings: Map<String, String> = mapOf(
        "app_title" to "BLUE GAS Pakistan",
        "app_tagline" to "Digital LPG Pilot (Lahore & Islamabad)",
        "surface_customer" to "Customer App",
        "surface_rider" to "Rider App",
        "surface_staff" to "Staff Portal",
        "role_operations" to "Operations",
        "role_finance" to "Finance",
        "role_distributor" to "Distributor",
        "role_support" to "Customer Support",
        "role_admin" to "Admin & Config",
        "role_inventory" to "Inventory Control",

        // Order statuses
        "ORD_DRAFT" to "Draft",
        "ORD_VALPEND" to "Validation Pending",
        "ORD_PAYPEND" to "Payment Pending",
        "ORD_CONF" to "Confirmed",
        "ORD_RSVD" to "Reserved",
        "ORD_ASGN" to "Assigned",
        "ORD_ACPT" to "Accepted",
        "ORD_PREP" to "Preparing",
        "ORD_DISP" to "Dispatched",
        "ORD_RDYPU" to "Ready for Pickup",
        "ORD_ATHO" to "At Handover",
        "ORD_INSP" to "Inspection Pending",
        "ORD_ADJ" to "Adjustment Pending",
        "ORD_HOC" to "Handover Complete",
        "ORD_EXC" to "Fulfilment Exception",
        "ORD_COMP" to "Completed",
        "ORD_CANC" to "Cancelled",
        "ORD_SUPS" to "Superseded",
        "ORD_FCLO" to "Failed Closed",

        // Actions
        "action_order_now" to "Place LPG Order",
        "action_pickup" to "Self Pickup",
        "action_delivery" to "Doorstep Delivery",
        "action_check_service" to "Check Serviceability",
        "action_apply_promo" to "Apply",
        "action_pay_online" to "Pay Online (Hosted Gateway)",
        "action_pay_cod" to "Cash on Delivery",
        "action_accept_order" to "Accept Order",
        "action_dispatch_order" to "Dispatch Order",
        "action_handover" to "Arrive at Handover",
        "action_complete_delivery" to "Complete Handover",
        "action_raise_complaint" to "Report Issue / Dispute",
        "action_recover_exception" to "Resolve Exception",
        "action_offline_toggle" to "Offline Mode",
        "action_replay_outbox" to "Sync Offline Outbox",
        "action_simulate_conflict" to "Simulate Serial Conflict",
        "action_dev_drawer" to "Dev Inspector",
        "action_reset_data" to "Reset Demo Data",

        // Labels
        "lbl_active_user" to "Active Identity:",
        "lbl_open_decision" to "Open Sequencing Decision (Under Technical Review)",
        "lbl_accountable_role" to "Accountable Role:",
        "lbl_hold_timer" to "Stock Hold Expires In:",
        "lbl_currency" to "Rs.",
        "lbl_cylinder_serial" to "Physical Cylinder Serial",
        "lbl_ownership" to "Ownership State",
        "lbl_custody" to "Physical Custody",
        "lbl_paisa_disclaimer" to "Configurable pricing values in Paisa (1 Rupee = 100 Paisa)",
        "lbl_ai_copilot" to "Blue Gas AI Advisor (gemini-4-argon with High Thinking)"
    )

    private val urStrings: Map<String, String> = mapOf(
        "app_title" to "بلیو گیس پاکستان",
        "app_tagline" to "ڈیجیٹل ایل پی جی پائلٹ (لاہور اور اسلام آباد)",
        "surface_customer" to "کسٹمر ایپ",
        "surface_rider" to "رائڈر ایپ",
        "surface_staff" to "اسٹاف پورٹل",
        "ORD_DRAFT" to "ڈرافٹ",
        "ORD_CONF" to "تصدیق شدہ",
        "ORD_PREP" to "تیاری میں",
        "ORD_DISP" to "روانہ کر دیا گیا",
        "ORD_COMP" to "مکمل",
        "action_order_now" to "آرڈر درج کریں"
    )

    fun t(key: String, fallback: String = key): String {
        return if (currentLanguage == "ur") {
            urStrings[key] ?: enStrings[key] ?: fallback
        } else {
            enStrings[key] ?: fallback
        }
    }
}
