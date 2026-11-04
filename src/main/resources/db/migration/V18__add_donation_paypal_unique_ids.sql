-- A PayPal order or capture belongs to a single donation. Besides keeping the data consistent, this backs the
-- idempotency checks in OnlineDonationService (the capture call and the webhook can both report the same payment).
-- NULLs don't collide, so cash and transfer donations are unaffected.
CREATE UNIQUE INDEX uq_donation_paypal_order_id ON donation (paypal_order_id);
CREATE UNIQUE INDEX uq_donation_paypal_capture_id ON donation (paypal_capture_id);
