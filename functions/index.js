const { initializeApp } = require("firebase-admin/app");
const { getDatabase, ServerValue } = require("firebase-admin/database");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onRequest } = require("firebase-functions/v2/https");
const kapitalBank = require("./kapitalBankClient");

initializeApp();
const db = getDatabase();

// TODO: real qiyməti təyin edin (AZN)
const UNLOCK_PRICE_AZN = 5;

function sanitizePhone(phone) {
  return String(phone || "").replace(/\D/g, "");
}

/**
 * Android tətbiqi bu funksiyanı çağırır (Firebase Functions SDK, callable).
 * Kapital Bank-da sifariş yaradır və checkout URL-ini qaytarır.
 */
exports.initiatePayment = onCall(async (request) => {
  const phone = sanitizePhone(request.data && request.data.phone);
  if (phone.length < 9) {
    throw new HttpsError("invalid-argument", "Telefon nömrəsi düzgün deyil.");
  }

  const callbackBaseUrl = process.env.APP_CALLBACK_BASE_URL;
  if (!callbackBaseUrl) {
    throw new HttpsError(
      "failed-precondition",
      "APP_CALLBACK_BASE_URL konfiqurasiya olunmayıb (deploy edilmiş " +
        "paymentApprove/paymentDecline/paymentCancel funksiyalarının bazası)."
    );
  }

  const orderRef = db.ref("paymentOrders").push();
  const internalOrderId = orderRef.key;

  await orderRef.set({
    phone,
    status: "pending",
    createdAt: ServerValue.TIMESTAMP,
  });

  let order;
  try {
    order = await kapitalBank.createOrder({
      amount: UNLOCK_PRICE_AZN,
      description: "Bütün sınaqların açılışı",
      approveUrl: `${callbackBaseUrl}/paymentApprove?internalOrderId=${internalOrderId}`,
      cancelUrl: `${callbackBaseUrl}/paymentCancel?internalOrderId=${internalOrderId}`,
      declineUrl: `${callbackBaseUrl}/paymentDecline?internalOrderId=${internalOrderId}`,
    });
  } catch (err) {
    await orderRef.update({ status: "failed", error: err.message });
    throw new HttpsError("internal", `Ödəniş başladıla bilmədi: ${err.message}`);
  }

  await orderRef.update({
    kapitalOrderId: order.orderId,
    kapitalSessionId: order.sessionId,
  });

  return { checkoutUrl: order.paymentUrl, internalOrderId };
});

/**
 * Kapital Bank istifadəçini ödənişdən sonra bu URL-ə yönləndirir.
 * Redirect-ə etibar etmirik — statusu bank tərəfindən yenidən yoxlayırıq,
 * yalnız bundan sonra "unlocked" bayrağını qoyuruq.
 */
exports.paymentApprove = onRequest(async (req, res) => {
  const internalOrderId = req.query.internalOrderId;
  const snapshot = await db.ref(`paymentOrders/${internalOrderId}`).get();
  const order = snapshot.val();

  if (!order) {
    res.status(404).send("Sifariş tapılmadı.");
    return;
  }

  try {
    const status = await kapitalBank.getOrderStatus({
      orderId: order.kapitalOrderId,
      sessionId: order.kapitalSessionId,
    });

    if (status.status === "paid") {
      await db.ref(`paymentOrders/${internalOrderId}`).update({ status: "paid" });
      await db.ref(`purchases/${order.phone}`).set({
        unlocked: true,
        updatedAt: ServerValue.TIMESTAMP,
      });
      res.send("Ödəniş uğurludur! Tətbiqə qayıdıb bağlaya bilərsiniz.");
    } else {
      res.send("Ödəniş hələ təsdiqlənməyib.");
    }
  } catch (err) {
    res.status(500).send(`Status yoxlanılarkən xəta: ${err.message}`);
  }
});

exports.paymentDecline = onRequest(async (req, res) => {
  const internalOrderId = req.query.internalOrderId;
  await db.ref(`paymentOrders/${internalOrderId}`).update({ status: "declined" });
  res.send("Ödəniş uğursuz oldu. Tətbiqə qayıdıb yenidən cəhd edə bilərsiniz.");
});

exports.paymentCancel = onRequest(async (req, res) => {
  const internalOrderId = req.query.internalOrderId;
  await db.ref(`paymentOrders/${internalOrderId}`).update({ status: "cancelled" });
  res.send("Ödəniş ləğv edildi.");
});
