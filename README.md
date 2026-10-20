# MyApplication — İmtahan/Test Tətbiqi

Kotlin və Jetpack Compose ilə yazılmış Android imtahan/test tətbiqi və onun öz serveri (Spring Boot + PostgreSQL). Şagirdlər telefon nömrəsi ilə qeydiyyatdan keçib sınaqlar (test) həll edir, müəllim isə admin panelindən sual bankını idarə edir.

## Xüsusiyyətlər

### Şagird tərəfi
- **Giriş**: rol seçimi ("Şagird" / "Müəllim") ilə başlayır
- **Qeydiyyat**: ilk dəfə telefon nömrəsi + ad-soyad + parol daxil edilir (SMS təsdiqi tələb olunmur)
- **Sonrakı girişlər**: nömrə + parol ilə. Hesab ilk daxil olduğu telefona bağlanır (paylaşılmasın deyə) — telefon dəyişəndə admin cihazı sıfırlayır
- **Sınaqlar**: 1–3-cü sınaqlar pulsuz, 4-cü sınaqdan yuxarı ödənişlidir
  - Ödəniş: kart-karta köçürmə (məbləğ və kart nömrəsi `local.properties`-dən gəlir) + admin əl ilə açır
  - Sınaqlar ekranı hər 15 saniyədən bir serverdən yenilənir — ödəniş təsdiqlənən kimi kilidlər götürülür
  - Server ödənişli sınaqların suallarını açılmamış hesaba vermir
- **İmtahan zamanı**: sual sayına mütənasib taймer (hər sual üçün 12 saniyə), dərhal düzgün/səhv geribildirimi, avtomatik növbəti suala keçid
- **Nəticə ekranı**: xal, uğur/uğursuzluq statusu (nəticə serverdə saxlanılır)

### Müəllim (Admin) tərəfi
- İki yerdən idarə olunur: tətbiqdəki "Müəllim" bölməsi və kompüter üçün **brauzer paneli** (`/admin`)
- Parolla qorunan giriş — parol yalnız serverdə (`.env`-dəki `IMTAHAN_ADMIN_PASSWORD`) yoxlanılır
- **Sınaqların idarəsi**: yeni sınaq (istəyə bağlı adla), sınağın suallarına baxmaq, sual və ya bütün sınağı silmək
- **Sual əlavə etmə**: tək-tək (mətn, 2–4 variant, düzgün cavab, istəyə bağlı şəkil) və ya toplu (bulk) mətn formatında
- **Əl ilə açılış** və **cihaz sıfırlama**: telefon nömrəsi ilə
- **Nəticələr**: şagirdlərin son imtahan nəticələri (ad, telefon, sınaq, xal, tarix)

### Brauzer paneli (`/admin`)

Kompüterdə sual bankı ilə rahat işləmək üçün. Server işləyən istənilən yerdə açılır:
lokal kompüterdə `http://localhost/admin`, serverdə `http://<SERVER_IP>/admin` (domen alınandan sonra `https://<domen>/admin`).

- Sualları əlavə etmək, **redaktə etmək** və silmək; sınağın adını dəyişmək
- `Ctrl+Enter` ilə saxlamaq, variantlar arasında `Enter` ilə keçmək
- Şəkli seçmək, sürükləyib atmaq və ya `Ctrl+V` ilə (məs. ekran görüntüsü) yapışdırmaq — böyük şəkillər avtomatik 1600px-ə kiçildilir
- Toplu yükləmə: yazdıqca neçə sualın hazır olduğunu göstərir
- Nəticələr (ad/telefonla axtarış), ödəniş açılışı və cihaz sıfırlama

### Toplu sual yükləmə formatı

Hər sual boş sətirlə ayrılır. Birinci sətir — sualın mətni, qalan sətirlər — variantlar (ən azı 2), düzgün variantın əvvəlinə `*` qoyulur:

```
Azərbaycanın paytaxtı hansıdır?
Gəncə
*Bakı
Şəki
Naxçıvan
```

## Arxitektura

```
Android tətbiqi  ──HTTPS/JSON──>  Caddy (reverse proxy, avtomatik HTTPS)
                                     │
                                     ▼
                                  Spring Boot API  ──>  PostgreSQL
                                     │
                                     └──>  /data/uploads (sual şəkilləri)
```

Server tərəfi bütünlüklə `backend/docker-compose.yml` ilə bir əmrlə qalxır.

### Layihə strukturu

```
app/src/main/java/com/example/myapplication/
├── MainActivity.kt      # Bütün Compose ekranları (UI)
├── TestViewModel.kt     # Biznes məntiqi + server sorğuları
├── Api.kt               # Retrofit API interfeysi və HTTP client
├── Models.kt            # Question, UserInfo, Category
└── ui/theme/            # Rəng palitri, tipoqrafiya

backend/
├── src/main/kotlin/com/example/backend/
│   ├── AuthController.kt    # qeydiyyat, giriş, admin girişi, /api/me
│   ├── QuizController.kt    # sınaqlar, suallar, nəticələr (şagird)
│   ├── AdminController.kt   # admin əməliyyatları, şəkil yükləmə
│   ├── Repositories.kt      # SQL sorğuları (JdbcClient)
│   └── Security.kt          # JWT, icazələr, giriş cəhdlərinin limiti
├── src/main/resources/db/migration/  # Flyway — verilənlər bazası sxemi
├── src/main/resources/static/admin/  # brauzer admin paneli (HTML/CSS/JS)
├── docker-compose.yml   # PostgreSQL + API + Caddy
├── Caddyfile
└── .env.example
```

### Verilənlər bazası (PostgreSQL)

| Cədvəl | Məzmun |
|---|---|
| `users` | telefon, ad, soyad, parolun bcrypt hash-i, bağlı cihaz ID-si |
| `purchases` | telefon → ödənişli sınaqlar açıqdırmı |
| `categories` | sınağın nömrəsi (1–3 pulsuz) və istəyə bağlı adı |
| `questions` | mətn, variantlar (`TEXT[]`), düzgün cavab, şəkil ünvanı |
| `results` | şagird, sınaq, xal, ümumi sual sayı, tarix |

Sxem tətbiq ilk dəfə işə düşəndə Flyway tərəfindən avtomatik yaradılır (`V1__init.sql`), 25 boş sınaqla.

### API

| Metod | Ünvan | Kim |
|---|---|---|
| POST | `/api/auth/check-phone`, `/api/auth/register`, `/api/auth/login`, `/api/auth/admin` | hamı |
| GET | `/api/categories` | hamı |
| GET | `/api/me`, `/api/categories/{n}/questions` · POST `/api/results` | şagird |
| GET/POST/DELETE | `/api/admin/...` (sınaqlar, suallar, şəkillər, açılış, cihaz sıfırlama) | admin |

Giriş JWT token ilə işləyir (`Authorization: Bearer ...`). Şagird token-i 180 gün, admin token-i 12 saat etibarlıdır.

## Serverə yerləşdirmə (Contabo VPS)

1. **VPS**: Contabo-da Ubuntu 24.04 ilə ən kiçik Cloud VPS kifayətdir. SSH ilə daxil olun.
2. **Docker quraşdırın**:
   ```bash
   curl -fsSL https://get.docker.com | sh
   ```
3. **Firewall** (yalnız SSH, HTTP, HTTPS açıq qalsın):
   ```bash
   ufw allow OpenSSH && ufw allow 80 && ufw allow 443 && ufw enable
   ```
4. **Layihəni gətirin və konfiqurasiya edin**:
   ```bash
   git clone <repo-url> && cd My_Application/backend
   cp .env.example .env
   nano .env   # parolları və JWT_SECRET-i dəyişin (openssl rand -base64 48)
   ```
5. **İşə salın**:
   ```bash
   docker compose up -d --build
   ```
   Yoxlama: `curl http://<SERVER_IP>/api/health` → `{"status":"ok"}`
6. **Tətbiqi serverə yönəldin** — `local.properties`-də:
   ```properties
   API_BASE_URL=http://<SERVER_IP>/
   ```
   Domen olmadan (HTTP) yalnız **debug** build işləyir — Android release build-də şifrəsiz HTTP-ni bloklayır.

### Domen alandan sonra (HTTPS)

1. Domenin DNS-ində `A` qeydi yaradın: `api.saytim.az → <SERVER_IP>`
2. `backend/.env`-də: `SITE_ADDRESS=api.saytim.az`
3. `docker compose up -d` — Caddy Let's Encrypt sertifikatını avtomatik alır və yeniləyir
4. `local.properties`-də: `API_BASE_URL=https://api.saytim.az/` və release APK yığın

### Yeniləmə və ehtiyat nüsxə

```bash
git pull && docker compose up -d --build          # yeni versiyanı yerləşdirmək
docker compose exec db pg_dump -U imtahan imtahan > backup-$(date +%F).sql   # bazanın nüsxəsi
docker compose logs -f api                         # server logları
```

Sual şəkilləri `uploads` Docker volume-undadır; onu da ehtiyat nüsxəyə daxil edin.

## Server alınmamışdan əvvəl: kompüterdə işlətmək

Sualları indidən kompüterinizdə daxil edib, sonra hamısını serverə köçürə bilərsiniz.

1. **Docker Desktop**-u açın.
2. `backend/.env` faylı yaradın (`.env.example`-dən) və parolları təyin edin.
3. İşə salın:
   ```bash
   cd backend
   docker compose up -d --build
   ```
4. Brauzerdə `http://localhost/admin` açın, `IMTAHAN_ADMIN_PASSWORD` parolu ilə daxil olun.

Kompüter yenidən başladıqdan sonra Docker Desktop-u açmaq kifayətdir — konteynerlər özləri qalxır.

### Kompüterdəki sualları serverə köçürmək

Server hazır olanda, kompüterdə **PowerShell**-də (Git Bash-da yox — o, `/tmp/...` yollarını Windows yoluna çevirib əmri pozur):
```bash
cd backend
docker compose exec db pg_dump -U imtahan --data-only --exclude-table=flyway_schema_history -f /tmp/data.sql imtahan
docker compose cp db:/tmp/data.sql ./data.sql
docker compose cp api:/data/uploads ./uploads-backup
```
`data.sql` faylını və `uploads-backup` qovluğunu serverdəki `backend` qovluğuna köçürün (məs. `scp`). Sonra serverdə, backend ilk dəfə işə düşüb cədvəlləri yaradandan sonra:
```bash
docker compose cp ./data.sql db:/tmp/data.sql
docker compose exec db psql -U imtahan imtahan -c "TRUNCATE categories, users, purchases, results RESTART IDENTITY CASCADE"
docker compose exec db psql -U imtahan imtahan -f /tmp/data.sql
docker compose cp ./uploads-backup/. api:/data/uploads
```

## Lokal inkişaf

- **Backend**: `cd backend && docker compose --env-file .env up -d --build` (və ya PostgreSQL-i ayrıca qaldırıb `./gradlew bootRun`). Lokalda Caddy 80-ci portda dinləyir.
- **Android emulyatoru**: `API_BASE_URL` təyin edilməyibsə, defolt `http://10.0.2.2:8080/` istifadə olunur (emulyatordan kompüterdəki `bootRun`-a). Docker Compose ilə işlədirsinizsə: `API_BASE_URL=http://10.0.2.2/`.
- Build:
  ```bash
  ./gradlew assembleDebug     # test üçün
  ./gradlew assembleRelease   # paylaşım üçün (imzalanmış, HTTPS domen tələb edir)
  ```

### `local.properties` nümunəsi

```properties
sdk.dir=/path/to/android/sdk

API_BASE_URL=http://10.0.2.2/

# Kart-karta köçürmə məlumatları
PAYMENT_CARD_NUMBER=0000 0000 0000 0000
PAYMENT_AMOUNT_AZN=5

# Release APK imza açarı (yalnız release build üçün lazımdır)
RELEASE_STORE_FILE=release-key.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

Bu fayl və `backend/.env` `.gitignore`-dadır — heç vaxt commit edilmir.

## Təhlükəsizlik qeydləri

- Parollar serverdə yalnız bcrypt hash şəklində saxlanılır.
- Giriş cəhdləri limitlidir: eyni IP-dən və ya eyni hesaba 15 dəqiqədə 10 uğursuz cəhddən sonra müvəqqəti bloklanır.
- Cihaz bağlaması server tərəfində yoxlanılır; admin cihazı sıfırlayanda köhnə telefondakı sessiya da etibarsız olur.
- Ödənişli sınaqların sualları server tərəfində qorunur — tətbiqi dəyişdirməklə açmaq olmur.
- PostgreSQL portu xaricə açılmır, yalnız API konteyneri ona qoşulur.
- Sual şəkilləri təsadüfi (UUID) adlarla `/uploads/...` ünvanında ictimai paylanır.

## Bilinən məhdudiyyətlər

- Ödəniş prosesi tam avtomatlaşdırılmayıb — admin köçürməni əl ilə yoxlayıb "aç" düyməsinə basmalıdır
- Admin panelinin "Nəticələr" tab-ı yalnız son 300 nəticəni göstərir (hamısı `results` cədvəlində saxlanılır)
