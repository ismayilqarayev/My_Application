const axios = require("axios");   // HTTP sorğuları göndərmək üçün kitabxana (npm paketi)
const https = require("https");   // Node.js-in daxili modulu - sertifikatlı (mTLS) bağlantı üçün

/**
 * ===========================================================================
 * KAPITAL BANK E-COMMERCE GATEWAY KLİENTİ
 * ===========================================================================
 * Bu fayl BAŞQA HEÇ NƏ ETMİR - yalnız Kapital Bank-ın API-si ilə "danışmaq"
 * üçün iki funksiya təqdim edir: sifariş yaratmaq (createOrder) və
 * sifarişin statusunu yoxlamaq (getOrderStatus).
 *
 * AUTENTİFİKASİYA NECƏ İŞLƏYİR (mTLS - qarşılıqlı sertifikat):
 * Adi API açarı (API key) əvəzinə, Kapital Bank sizə bir "sertifikat" (cert)
 * və "açar" (key) faylı verir. Hər sorğu bu sertifikatla "imzalanır" ki, bank
 * sorğunun həqiqətən sizdən gəldiyini bilsin. Bu, adi parol/API-key-dən daha
 * təhlükəsiz üsuldur.
 *
 * VACİB QEYD: pg.kapitalbank.az/docs səhifəsi JavaScript ilə render olunur və
 * mən onu avtomatik oxuya bilmədim. Aşağıdakı sahə adları (merchantId,
 * approveUrl/cancelUrl/declineUrl, cavabda orderId/sessionId/paymentUrl)
 * icma kitabxanasından (github.com/eyvazoff/kapitalbank) təsdiqlənib, AMMA
 * dəqiq endpoint yollarını ("/order/register", "/order/status") merchant
 * hesabı aldıqdan sonra rəsmi sənədlərdən MÜTLƏQ yoxlayın.
 *
 * LAZIM OLAN ƏTRAF MÜHIT DƏYIŞƏNLƏRI (environment variables):
 * Bunlar Firebase-də "gizli" (secret) kimi saxlanmalıdır, HEÇ VAXT bu
 * fayla və ya repoya yazılmamalıdır:
 *   KAPITAL_MERCHANT_ID  - Kapital Bank-ın sizə verdiyi merchant nömrəsi
 *   KAPITAL_CERT_PEM     - client sertifikatı (PEM formatında mətn)
 *   KAPITAL_KEY_PEM      - client-in məxfi açarı (PEM formatında mətn)
 *   KAPITAL_LIVE_MODE    - "true" olduqda canlı (real pul) mühit, əks halda sandbox (test)
 * ===========================================================================
 */

/**
 * Hansı mühitdə olduğumuza (test/canlı) görə düzgün bazasını (base URL) seçir.
 */
function getBaseUrl() {
  return process.env.KAPITAL_LIVE_MODE === "true"
    ? "https://pg.kapitalbank.az" // TODO: rəsmi sənədlərdən təsdiqləyin (canlı mühit)
    : "https://tstpg.kapitalbank.az"; // sandbox (test) mühiti - əvvəlcə HƏMİŞƏ bununla test edin
}

/**
 * mTLS (qarşılıqlı sertifikat) bağlantısı üçün lazım olan HTTPS "agent"i yaradır.
 * Bu, Node.js-ə deyir: "bu sorğunu göndərəndə bizim sertifikatımızı da göstər".
 */
function createHttpsAgent() {
  return new https.Agent({
    cert: process.env.KAPITAL_CERT_PEM,
    key: process.env.KAPITAL_KEY_PEM,
    rejectUnauthorized: true,   // bankın öz sertifikatının da etibarlı olduğunu yoxlayır (təhlükəsizlik üçün vacib, false etməyin)
  });
}

/**
 * Lazımi mühit dəyişənləri (merchant ID, sertifikat) hələ təyin edilməyibsə,
 * aydın bir xəta ilə dərhal dayandırır. Bu, "sükutla uğursuz olmaq" əvəzinə
 * problemi açıq şəkildə göstərir.
 */
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
 * Kapital Bank-da YENİ bir ödəniş sifarişi açır.
 *
 * @param {object} params
 * @param {number} params.amount - ödəniş məbləği (AZN)
 * @param {string} params.description - sifarişin təsviri (bankın ödəniş səhifəsində görünür)
 * @param {string} params.approveUrl - ödəniş UĞURLU olanda istifadəçinin yönləndiriləcəyi link
 * @param {string} params.cancelUrl - istifadəçi ödənişi LƏĞV edəndə yönləndiriləcəyi link
 * @param {string} params.declineUrl - bank ödənişi RƏDD edəndə yönləndiriləcəyi link
 * @returns {Promise<{orderId: string, sessionId: string, paymentUrl: string}>}
 *   paymentUrl - istifadəçinin kart məlumatlarını daxil edəcəyi, bankın öz səhifəsinin linki
 */
async function createOrder({ amount, description, approveUrl, cancelUrl, declineUrl }) {
  assertConfigured();

  const response = await axios.post(
    `${getBaseUrl()}/order/register`, // TODO: dəqiq path-i rəsmi sənədlərdən təsdiqləyin
    {
      merchantId: process.env.KAPITAL_MERCHANT_ID,
      amount,
      currency: "944", // AZN-in beynəlxalq (ISO 4217) kodu
      description,
      approveUrl,
      cancelUrl,
      declineUrl,
      language: "AZ",
    },
    { httpsAgent: createHttpsAgent() }   // sorğunu sertifikatımızla "imzalayırıq"
  );

  return response.data;
}

/**
 * Verilmiş sifarişin HƏQİQİ ödəniş statusunu bankdan soruşur.
 *
 * ÇOX VACİB: bu funksiyanı çağırmadan, sadəcə "approveUrl-ə yönləndirildi"
 * deyə ödənişi "uğurlu" SAYMA - kimsə həmin linki saxta şəkildə çağıra bilər.
 * Ödənişi yalnız BU funksiyanın "paid" cavabından sonra təsdiq et.
 *
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

// Bu iki funksiyanı başqa fayllardan (index.js) istifadə edə bilmək üçün "ixrac" edirik
module.exports = { createOrder, getOrderStatus };
