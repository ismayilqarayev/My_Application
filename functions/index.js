// ==========================================================================
// BU FAYL FIREBASE CLOUD FUNCTIONS-DIR - yəni Google-un serverlərində
// işləyən, mobil tətbiqin GÖRMƏDİYİ backend kodudur.
//
// Niyə buna ehtiyac var? Çünki Kapital Bank-ın "sirr açarı" (sertifikat)
// heç vaxt telefon tətbiqinin daxilində saxlanıla bilməz - kimsə APK-nı
// "açıb" onu oğurlaya bilər. Ona görə bütün bank ilə əlaqə BURADA, təhlükəsiz
// serverdə edilir. Android tətbiqi yalnız bu faylın funksiyalarını "çağırır"
// və nəticəni gözləyir, bank ilə birbaşa danışmır.
// ==========================================================================

const { initializeApp } = require("firebase-admin/app");
const { getDatabase, ServerValue } = require("firebase-admin/database");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onRequest } = require("firebase-functions/v2/https");
const kapitalBank = require("./kapitalBankClient");   // Kapital Bank ilə danışan ayrıca fayl

// Firebase Admin SDK-nı işə salırıq - bu, Cloud Functions-a Realtime Database-ə
// TAM səlahiyyətlə (təhlükəsizlik qaydalarını keçərək) yazmaq imkanı verir.
initializeApp();
const db = getDatabase();

// TODO: real qiyməti təyin edin (AZN) - hazırda 5 AZN nümunə üçün qoyulub
const UNLOCK_PRICE_AZN = 5;

/**
 * Telefon nömrəsindən rəqəm olmayan hər şeyi təmizləyir.
 * Firebase-in açar (key) adlarında "+", "-", boşluq kimi simvollar
 * işlədilə bilməz, ona görə hər yerdə YALNIZ rəqəmlərdən istifadə edirik.
 */
function sanitizePhone(phone) {
  return String(phone || "").replace(/\D/g, "");
}

/**
 * ===========================================================================
 * FUNKSİYA 1: initiatePayment
 * ===========================================================================
 * Android tətbiqi bu funksiyanı "callable" (Firebase Functions SDK vasitəsilə,
 * birbaşa funksiya adı ilə) çağırır - adi HTTP linki deyil.
 *
 * NƏ EDİR:
 *  1) Telefon nömrəsini yoxlayır
 *  2) Firebase-də "gözləyən sifariş" qeydi yaradır (paymentOrders/...)
 *  3) Kapital Bank-dan sifariş açmasını xahiş edir (kapitalBank.createOrder)
 *  4) Bank-ın qaytardığı ödəniş linkini (checkoutUrl) Android tətbiqinə qaytarır
 *
 * Android tərəfi bu linki alıb Chrome Custom Tabs-da açır, istifadəçi
 * kart məlumatlarını ORADA (bizim tətbiqin xaricində, bankın öz səhifəsində) yazır.
 */
exports.initiatePayment = onCall(async (request) => {
  const phone = sanitizePhone(request.data && request.data.phone);
  if (phone.length < 9) {
    // "HttpsError" - Android tərəfində "addOnFailureListener"-ə düşən xəta növüdür
    throw new HttpsError("invalid-argument", "Telefon nömrəsi düzgün deyil.");
  }

  // Bu URL bazası, bankın ödəniş bitəndə istifadəçini hara yönləndirəcəyini bildirir.
  // Deploy edilmiş funksiyaların ünvanı olmalıdır (məs. https://REGION-PROJECT.cloudfunctions.net)
  const callbackBaseUrl = process.env.APP_CALLBACK_BASE_URL;
  if (!callbackBaseUrl) {
    throw new HttpsError(
      "failed-precondition",
      "APP_CALLBACK_BASE_URL konfiqurasiya olunmayıb (deploy edilmiş " +
        "paymentApprove/paymentDecline/paymentCancel funksiyalarının bazası)."
    );
  }

  // Firebase-də YENİ, unikal açarlı bir "sifariş" qeydi yaradırıq.
  // "push()" hər dəfə fərqli (təkrarlanmayan) bir ID yaradır.
  const orderRef = db.ref("paymentOrders").push();
  const internalOrderId = orderRef.key;

  // Sifarişi əvvəlcə "pending" (gözləmədə) statusu ilə yazırıq
  await orderRef.set({
    phone,
    status: "pending",
    createdAt: ServerValue.TIMESTAMP,   // Firebase-in öz server saatından vaxt möhürü
  });

  let order;
  try {
    // Kapital Bank-a "mənə bir ödəniş sifarişi aç" deyirik.
    // approve/cancel/decline URL-ləri - bank istifadəçini ödənişdən sonra
    // BU funksiyalara (aşağıda) yönləndirəcək.
    order = await kapitalBank.createOrder({
      amount: UNLOCK_PRICE_AZN,
      description: "Bütün sınaqların açılışı",
      approveUrl: `${callbackBaseUrl}/paymentApprove?internalOrderId=${internalOrderId}`,
      cancelUrl: `${callbackBaseUrl}/paymentCancel?internalOrderId=${internalOrderId}`,
      declineUrl: `${callbackBaseUrl}/paymentDecline?internalOrderId=${internalOrderId}`,
    });
  } catch (err) {
    // Bank ilə əlaqə uğursuz olarsa, sifarişi "failed" kimi qeyd edib xəta qaytarırıq
    await orderRef.update({ status: "failed", error: err.message });
    throw new HttpsError("internal", `Ödəniş başladıla bilmədi: ${err.message}`);
  }

  // Bankın bizə verdiyi ID-ləri sifariş qeydimizə əlavə edirik ki, sonra
  // (paymentApprove funksiyasında) statusu yoxlaya bilək
  await orderRef.update({
    kapitalOrderId: order.orderId,
    kapitalSessionId: order.sessionId,
  });

  // Android tətbiqinə checkout linkini qaytarırıq - o bunu brauzerdə açacaq
  return { checkoutUrl: order.paymentUrl, internalOrderId };
});

/**
 * ===========================================================================
 * FUNKSİYA 2: paymentApprove
 * ===========================================================================
 * Bu, adi bir HTTP ünvandır (callable YOX) - Kapital Bank istifadəçinin
 * brauzerini ödəniş uğurlu olanda BU linkə yönləndirir.
 *
 * ÇOX VACİB TƏHLÜKƏSİZLİK QAYDASI: brauzerin bu linkə "gəlməsinə" özü-özlüyündə
 * ETİBAR ETMİRİK - kimsə bu linki əlində saxlayıb özü çağıra bilərdi (ödəniş
 * etmədən "uğurlu" kimi göstərmək üçün). Ona görə HƏMİŞƏ bankın öz
 * "getOrderStatus" sorğusu ilə YENİDƏN yoxlayırıq, YALNIZ bank həqiqətən
 * "ödənilib" desə, "unlocked" bayrağını qoyuruq.
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
    // Redirect-ə etibar ETMİRİK - statusu bankdan YENİDƏN soruşuruq
    const status = await kapitalBank.getOrderStatus({
      orderId: order.kapitalOrderId,
      sessionId: order.kapitalSessionId,
    });

    if (status.status === "paid") {
      // Yalnız BURADA, real təsdiqdən sonra, açılış bayrağını qoyuruq.
      // Android tətbiqi bu sahəni CANLI dinlədiyi üçün dərhal xəbər tutub
      // özü açılacaq (heç bir əlavə iş görmək lazım deyil).
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

/**
 * FUNKSİYA 3: paymentDecline
 * Bank ödənişi RƏDD edəndə (məs. kartda pul çatmayanda) istifadəçi bura yönləndirilir.
 * Sadəcə sifarişin statusunu "declined" kimi qeyd edirik - açılış olmur.
 */
exports.paymentDecline = onRequest(async (req, res) => {
  const internalOrderId = req.query.internalOrderId;
  await db.ref(`paymentOrders/${internalOrderId}`).update({ status: "declined" });
  res.send("Ödəniş uğursuz oldu. Tətbiqə qayıdıb yenidən cəhd edə bilərsiniz.");
});

/**
 * FUNKSİYA 4: paymentCancel
 * İstifadəçi özü ödənişi LƏĞV edəndə (bank səhifəsindən geri çıxanda) bura yönləndirilir.
 */
exports.paymentCancel = onRequest(async (req, res) => {
  const internalOrderId = req.query.internalOrderId;
  await db.ref(`paymentOrders/${internalOrderId}`).update({ status: "cancelled" });
  res.send("Ödəniş ləğv edildi.");
});
