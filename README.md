# MyApplication — İmtahan/Test Tətbiqi

Kotlin və Jetpack Compose ilə yazılmış, Firebase backend-li Android imtahan/test tətbiqi. Şagirdlər telefon nömrəsi ilə qeydiyyatdan keçib sınaqlar (test) həll edir, müəllim isə admin panelindən sual bankını idarə edir.

## Xüsusiyyətlər

### Şagird tərəfi
- **Giriş**: rol seçimi ("Şagird" / "Müəllim") ilə başlayır
- **Qeydiyyat**: ilk dəfə telefon nömrəsi + ad-soyad + parol daxil edilir (SMS təsdiqi tələb olunmur)
- **Sonrakı girişlər**: istənilən cihazdan sadəcə nömrə + parol ilə (Firebase Auth Email/Parol provayderi)
- **Sınaqlar**: 1–3-cü sınaqlar pulsuz, 4-cü sınaqdan yuxarı ödənişlidir
  - Ödəniş: kart-karta köçürmə (məbləğ və kart nömrəsi `local.properties`-dən gəlir) + admin əl ilə açır
  - Açılış statusu Firebase-dən canlı (real-time) izlənilir — ödəniş təsdiqlənən kimi tətbiq avtomatik açılır
- **İmtahan zamanı**:
  - Sual sayına mütənasib geri sayan taймer (hər sual üçün 12 saniyə)
  - Cavab seçiləndə dərhal geribildirim: düzgün variant yaşıl ✓, seçilən səhv variant qırmızı ✗
  - Qısa gecikmədən sonra avtomatik növbəti suala keçid
  - Bütün ekranlarda geri qayıtma düyməsi
- **Nəticə ekranı**: xal, uğur/uğursuzluq statusu

### Müəllim (Admin) tərəfi
- Gizli parolla qorunan giriş (`local.properties` → `ADMIN_PASSWORD`)
- **Sınaqların idarəsi**:
  - Sınaqların sayı sabit deyil — "+ Yeni sınaq" düyməsi ilə istənilən qədər artırıla bilər
  - Hər sınağın içindəki bütün sualların siyahısına baxmaq, elə oradan yeni sual əlavə etmək
  - Tək-tək sual silmək və ya bütün sınağı (içindəki bütün suallarla) silmək (təsdiq dialoqu ilə)
- **Sual əlavə etmə**:
  - Tək-tək: mətn, 4 variant, düzgün cavab, istəyə bağlı şəkil (Firebase Storage-a yüklənir)
  - Toplu (bulk): xüsusi mətn formatında bir neçə sualı eyni anda yükləmək
- **Əl ilə açılış**: telefon nömrəsi yazıb şagirdin ödənişli sınaqlarını açmaq

### Toplu sual yükləmə formatı

Hər sual boş sətirlə ayrılır. Birinci sətir — sualın mətni, qalan sətirlər — variantlar, düzgün variantın əvvəlinə `*` qoyulur:

```
Azərbaycanın paytaxtı hansıdır?
Gəncə
*Bakı
Şəki
Naxçıvan

2+2 neçə edir?
3
*4
5
6
```

## Texnologiyalar

- **Kotlin** + **Jetpack Compose** (Material3) — UI
- **Firebase Realtime Database** — suallar, nəticələr, istifadəçi/ödəniş məlumatları
- **Firebase Authentication** (Email/Parol provayderi) — hesab sistemi
- **Firebase Storage** — sual şəkilləri
- **Coil** — şəkil yükləmə
- Gradle Kotlin DSL, version catalog (`libs.versions.toml`)

## Layihə strukturu

```
app/src/main/java/com/example/myapplication/
├── MainActivity.kt        # Bütün Compose ekranları (UI)
├── TestViewModel.kt        # Bütün biznes məntiqi + Firebase əməliyyatları
├── Models.kt                # Question, UserInfo data class-ları
└── ui/theme/                # Rəng palitri, tipoqrafiya (Birbank-dan ilhamlanıb)
```

Arxitektura: sadə **MVVM** — `TestViewModel` bütün state və Firebase məntiqini saxlayır, `MainActivity.kt`-dəki Composable-lar isə yalnız sadə parametrlər (`Boolean`, `String`, funksiyalar) qəbul edir və Firebase-ə birbaşa toxunmur. Bu, Android Studio-nun `@Preview` panelinin Firebase-siz işləməsinə imkan verir.

## Firebase Realtime Database strukturu

```
questions/
  "sınaq 1"/
    <sualId>/ { id, text, options: [...], correctAnswerIndex, imageUrl }
  ...
results/
  <avtomatik-id>/ { firstName, lastName, score, total, timestamp }
purchases/
  <telefon>/ { unlocked, updatedAt }
users/
  <telefon>/ { firstName, lastName }
meta/
  categoryCount: <Int>              # cəmi sınaq sayı
  deletedCategories/
    <N>: true                        # silinmiş sınaq nömrələri
```

## Quraşdırma

1. **Firebase layihəsi**: [Firebase Console](https://console.firebase.google.com)-da yeni layihə yaradın (və ya mövcud olanı istifadə edin)
   - Realtime Database yaradın, `database.rules.json` faylını deploy edin
   - Storage aktivləşdirin, `storage.rules` faylını deploy edin
   - Authentication → Sign-in method → **Email/Password** provayderini aktivləşdirin
   - Android tətbiqi qeydiyyatdan keçirin, `google-services.json` faylını `app/` qovluğuna qoyun
2. **`local.properties`** faylını layihənin kök qovluğunda yaradın (nümunə üçün aşağıya baxın)
3. **Release imza açarı** lazımdırsa (`./gradlew assembleRelease` üçün), `keytool` ilə `.jks` açarı yaradın və yolunu `local.properties`-də göstərin
4. Build:
   ```bash
   ./gradlew assembleDebug     # test üçün
   ./gradlew assembleRelease   # paylaşım üçün (imzalanmış)
   ```

### `local.properties` nümunəsi

```properties
sdk.dir=/path/to/android/sdk

# Admin panelinə giriş parolu (öz seçdiyiniz dəyərlə əvəz edin)
ADMIN_PASSWORD=your_admin_password_here

# Kart-karta köçürmə məlumatları
PAYMENT_CARD_NUMBER=0000 0000 0000 0000
PAYMENT_AMOUNT_AZN=5

# Release APK imza açarı (yalnız release build üçün lazımdır)
RELEASE_STORE_FILE=release-key.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

Bu fayl `.gitignore`-dadır — heç vaxt commit edilmir.

## Təhlükəsizlik qeydləri

- Parollar heç vaxt açıq mətn kimi saxlanılmır — Firebase Authentication-un öz (hash-lənmiş) mexanizmi istifadə olunur. Telefon nömrəsindən sintetik email düzəldilir (`<nömrə>@<layihə>.local`) və bu, Email/Parol provayderinin "istifadəçi adı" kimi işlədilir.
- `database.rules.json`-dakı qaydalar kiçik/şəxsi layihə üçün sadələşdirilib (əksər düyünlər açıq oxuma/yazmaya icazə verir). Genişmiqyaslı və ya həssas istifadə üçün daha ciddi qaydalar tələb olunur.
- Admin parolu və ödəniş kartı məlumatları `local.properties` vasitəsilə `BuildConfig`-ə keçirilir — mənbə kodunda (və GitHub-da) görünmür.

## Bilinən məhdudiyyətlər

- Ödəniş prosesi tam avtomatlaşdırılmayıb — admin köçürməni əl ilə yoxlayıb "aç" düyməsinə basmalıdır (bank API inteqrasiyası yoxdur, sadə və pulsuz saxlamaq üçün qəsdən belədir)
- Müəllim panelində şagird nəticələrinə baxmaq üçün ayrıca ekran hələ yoxdur (nəticələr Firebase-də saxlanılır, amma admin UI-də göstərilmir)
