package com.cosmos.app.data.payment

import android.app.Activity
import android.widget.Toast

/**
 * Breakdown details for a membership purchase transaction.
 */
data class PaymentDetails(
    val subtotal: Double,
    val gst: Double,
    val grandTotal: Double
)

/**
 * PaymentManager handles the client-side pricing computations.
 */
object PaymentManager {
    /**
     * Compute the billing breakdown (18% GST applied).
     */
    fun calculatePayment(price: Double): PaymentDetails {
        val subtotal = price
        val gst = if (subtotal > 0) subtotal * 0.18 else 0.0
        val grandTotal = subtotal + gst
        return PaymentDetails(subtotal, gst, grandTotal)
    }
}

