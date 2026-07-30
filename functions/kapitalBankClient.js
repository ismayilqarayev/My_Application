const axios = require("axios");
const https = require("https");

/**
 * Kapital Bank E-commerce Gateway (mTLS sertifikat əsaslı) klienti.
 *
 * VACİB: pg.kapitalbank.az/docs JavaScript ilə render olunan bir portaldır və
 * avtomatik oxuna bilmədi. Aşağıdakı sahə adları (merchantId, approveUrl/
 * cancelUrl/declineUrl, cavabda orderId/sessionId/paymentUrl) icma
 * kitabxanasından (github.com/eyvazoff/kapitalbank) təsdiqlənib, AMMA dəqiq
 * endpoint path-lərini ("/order/register", "/order/status") merchant hesabı
 * aldıqdan sonra rəsmi sənədlərdən (pg.kapitalbank.az/docs) mütləq yoxlayın
 * və lazım gələrsə düzəldin.
 *
 * Lazım olan env dəyişənləri (Firebase-də "secrets" kimi saxlanmalıdır,
 * heç vaxt repo-ya commit edilməməlidir):
 *   KAPITAL_MERCHANT_ID
 *   KAPITAL_CERT_PEM   - client sertifikatı (PEM formatında)
 *   KAPITAL_KEY_PEM    - client private key (PEM formatında)
 *   KAPITAL_LIVE_MODE  - "true" olduqda canlı mühit, əks halda sandbox
 */

function getBaseUrl() {
  return process.env.KAPITAL_LIVE_MODE === "true"
    ? "https://pg.kapitalbank.az" // TODO: rəsmi sənədlərdən təsdiqləyin
    : "https://tstpg.kapitalbank.az"; // sandbox (test) mühiti
}

function createHttpsAgent() {
  return new https.Agent({
    cert: process.env.KAPITAL_CERT_PEM,
    key: process.env.KAPITAL_KEY_PEM,
    rejectUnauthorized: true,
  });
}

function assertConfigured() {
  if (
    !process.env.KAPITAL_MERCHANT_ID ||
    !process.env.KAPITAL_CERT_PEM ||
    !process.env.KAPITAL_KEY_PEM
  ) {
    throw new Error(
      "Kapital Bank merchant məlumatları konfiqurasiya olunmayıb " +
        "(KAPITAL_MERCHANT_ID / KAPITAL_CERT_PEM / KAPITAL_KEY_PEM). " +
        "Merchant hesabı aldıqdan sonra bu dəyərləri əlavə edin."
    );
  }
}

/**
 * Yeni ödəniş sifarişi yaradır.
 * @returns {Promise<{orderId: string, sessionId: string, paymentUrl: string}>}
 */
async function createOrder({ amount, description, approveUrl, cancelUrl, declineUrl }) {
  assertConfigured();

  const response = await axios.post(
    `${getBaseUrl()}/order/register`, // TODO: dəqiq path-i sənədlərdən təsdiqləyin
    {
      merchantId: process.env.KAPITAL_MERCHANT_ID,
      amount,
      currency: "944", // AZN (ISO 4217)
      description,
      approveUrl,
      cancelUrl,
      declineUrl,
      language: "AZ",
    },
    { httpsAgent: createHttpsAgent() }
  );

  return response.data;
}

/**
 * Sifarişin real ödəniş statusunu yoxlayır.
 * VACİB: bank tərəfindən brauzer redirect-inə HEÇ VAXT etibar etmə —
 * ödənişi "uğurlu" saymadan əvvəl mütləq bu funksiya ilə statusu təsdiqlə.
 * @returns {Promise<{status: "paid" | "pending" | "declined", [key: string]: any}>}
 */
async function getOrderStatus({ orderId, sessionId }) {
  assertConfigured();

  const response = await axios.get(`${getBaseUrl()}/order/status`, {
    params: { orderId, sessionId },
    httpsAgent: createHttpsAgent(),
  });

  return response.data;
}

module.exports = { createOrder, getOrderStatus };
