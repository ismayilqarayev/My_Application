package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.myapplication.BuildConfig
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.util.Locale

// ==========================================================================
// BU FAYL TƏTBIQIN BÜTÜN EKRANLARINI (Compose UI) SAXLAYIR.
// Burada YALNIZ görünüş var - hansı düymə hara basılır, nə rəngdədir və s.
// Məntiq (Firebase, hesablama, taймer) TestViewModel.kt-dədir, bu fayl
// yalnız oradakı state-ə "baxıb" ekranı çəkir.
//
// Compose necə işləyir (qısaca): hər "@Composable" funksiya bir ekran
// hissəsini təsvir edir. İçindəki dəyər (state) dəyişəndə, Compose YALNIZ
// dəyişən hissəni yenidən çəkir - bütün ekranı yox.
// ==========================================================================

/**
 * Tətbiqin giriş nöqtəsi (Android bu sinifi ilk açır).
 * Burada başqa heç nə yoxdur - sadəcə Compose "dünyasına" keçid edir
 * və TestApp() adlı əsas ekranı göstərir.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()   // ekranı tam ekran (status bar-ın altına qədər) çəkməyə imkan verir
        setContent {
            MyApplicationTheme {   // rəng/şrift temasını bütün tətbiqə tətbiq edir (bax: ui/theme/ qovluğu)
                TestApp()
            }
        }
    }
}

/**
 * Tətbiqin ƏSAS "yönləndirici"si (router).
 * "viewModel.screenState" dəyişəndə, aşağıdakı "when" bloku hansı ekranın
 * göstəriləcəyini seçir. Yəni bütün ekran keçidləri (navigation) bu bir
 * funksiyanın içindən idarə olunur.
 *
 * "viewModel: TestViewModel = viewModel()" - bu, Android-ə deyir: "mənə
 * TestViewModel-in bir nüsxəsini ver". Android bunu avtomatik yaradır və
 * ekran fırlansa belə (rotation) eyni nüsxəni saxlayır.
 */
@Composable
fun TestApp(viewModel: TestViewModel = viewModel()) {
    // Ödəniş dialoqunun görünüb-görünməməsini idarə edən yerli (local) state.
    // Bu, ViewModel-də deyil, burada saxlanılır, çünki sadəcə "dialoq açıqdır/bağlıdır"
    // sualının cavabıdır - Firebase-ə aid deyil.
    var showPaymentDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // "AnimatedContent" - ekranlar arasında keçiddə yumşaq keçid effekti (fade) yaradır
        AnimatedContent(
            targetState = viewModel.screenState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ScreenTransition"
        ) { state ->
            // Hər bir ekran vəziyyətinə uyğun composable-ı çağırırıq.
            // Diqqət: heç bir yerdə "başqa ekrana keç" məntiqi yoxdur -
            // sadəcə viewModel.screenState-i dəyişirik, qalanını Compose edir.
            when (state) {
                ScreenState.Registration -> RegistrationScreen(
                    onRegister = viewModel::register,
                    onAdminClick = { viewModel.screenState = ScreenState.Admin }
                )
                ScreenState.CategorySelection -> CategorySelectionScreen(
                    isUnlocked = viewModel.isUnlocked,
                    onCategorySelected = viewModel::selectCategory,
                    onLockedClick = { showPaymentDialog = true }   // kilidli sınağa klik -> ödəniş dialoqu aç
                )
                ScreenState.Testing -> {
                    // Suallar hələ yüklənməyibsə (boşdursa), heç nə göstərmirik ki,
                    // "IndexOutOfBounds" xətası olmasın (aşağıda questions[index] istifadə olunur)
                    if (viewModel.questions.isNotEmpty()) {
                        QuestionScreen(
                            question = viewModel.questions[viewModel.currentQuestionIndex],
                            currentNum = viewModel.currentQuestionIndex + 1,
                            totalNum = viewModel.questions.size,
                            timeLeft = viewModel.timeLeft,
                            onAnswer = viewModel::answerQuestion
                        )
                    }
                }
                ScreenState.Result -> ResultScreen(
                    userInfo = viewModel.userInfo,
                    score = viewModel.score,
                    total = viewModel.questions.size,
                    onRestart = viewModel::restart
                )
                ScreenState.Admin -> AdminScreen(
                    isLoading = viewModel.isLoadingQuestions,
                    onBack = { viewModel.screenState = ScreenState.Registration },
                    onUpload = viewModel::uploadQuestion
                )
                ScreenState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Tezliklə...") }
            }
        }

        // Bu blok "when" bloklarının XARİCİNDƏDİR - yəni hansı ekranda olursa
        // olsun, xəta varsa bu AlertDialog onun ÜZƏRİNDƏ görünür.
        viewModel.errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::clearError,
                title = { Text("Xəta") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = viewModel::clearError) { Text("Tamam") }
                }
            )
        }

        // Eyni məntiqlə, ödəniş dialoqu da bütün ekranların üzərində göstərilə bilər
        if (showPaymentDialog) {
            PaymentDialog(viewModel = viewModel, onDismiss = { showPaymentDialog = false })
        }
    }
}

/**
 * Tətbiqin İLK ekranı: şagird ad-soyadını daxil edir.
 * Sağ-alt küncdəki gizli (şəffaf) parametr ikonu admin girişini açır.
 */
@Composable
fun RegistrationScreen(onRegister: (String, String) -> Unit, onAdminClick: () -> Unit) {
    // Bu ekrana məxsus, müvəqqəti (yalnız bu ekran açıq olduqca yaşayan) state-lər.
    // "remember" - Compose-a deyir: "bu dəyəri, ekran yenidən çəkilsə belə, saxla".
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }

    // Admin girişi üçün state-lər
    var showAdminLogin by remember { mutableStateOf(false) }   // parol dialoqu görünsün/görünməsin
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }           // yanlış parol yazılıbsa true olur

    // Admin giriş dialoqu (parol soruşan pəncərə)
    if (showAdminLogin) {
        AlertDialog(
            onDismissRequest = { showAdminLogin = false },
            title = { Text("Admin Girişi", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Zəhmət olmasa admin parolunu daxil edin:")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            isError = false   // yenidən yazmağa başlayanda köhnə xəta mesajını gizlət
                        },
                        label = { Text("Parol") },
                        singleLine = true,
                        isError = isError,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (isError) {
                        Text(
                            text = "Yanlış parol!",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Parol BuildConfig-dən oxunur (local.properties -> ADMIN_PASSWORD),
                        // koda "hardcode" edilməyib ki, mənbə koduna baxan hər kəs görməsin.
                        if (password == BuildConfig.ADMIN_PASSWORD) {
                            showAdminLogin = false
                            onAdminClick()
                        } else {
                            isError = true
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Daxil Ol")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminLogin = false }) {
                    Text("Ləğv Et")
                }
            }
        )
    }

    // Ekranın əsas gövdəsi: gradient fon + ortada qeydiyyat kartı
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Xoş Gəlmisiniz",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Məlumatlarınızı daxil edin",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    // Ad sahəsi
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("Ad") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Soyad sahəsi
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Soyad") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { onRegister(firstName, lastName) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        // Ad və soyad boş olduqca düymə deaktiv qalır (basıla bilməz)
                        enabled = firstName.isNotBlank() && lastName.isNotBlank()
                    ) {
                        Text("İmtahana Başla", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Sağ-alt küncdə, demək olar ki, görünməyən (şəffaflığı 0.3) admin düyməsi.
        // Adi istifadəçi bunu fərq etməz, amma admin bilərək klikləyə bilər.
        IconButton(
            onClick = { showAdminLogin = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Admin",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * Kateqoriya (sınaq) seçimi ekranı: 1-dən 25-ə qədər sınaqları 3-lük
 * sıralarla göstərir. 4-cü sınaqdan yuxarı, ödəniş edilməyibsə, kilidli olur.
 *
 * DİQQƏT: Bu funksiya bilərəkdən TestViewModel-i BİRBAŞA qəbul ETMİR -
 * yalnız sadə parametrlər (Boolean, funksiyalar) alır. Bu, iki səbəbə görədir:
 *  1) Android Studio-nun preview (canlı önizləmə) paneli əsl Firebase-ə
 *     qoşula bilmir, ona görə TestViewModel-i birbaşa versək önizləmə xəta verir.
 *  2) Composable-ı ViewModel-dən ayırmaq kodu daha təmiz və test edilə bilən edir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectionScreen(
    isUnlocked: Boolean,
    onCategorySelected: (String) -> Unit,
    onLockedClick: () -> Unit
) {
    // 25 ədəd "sınaq N" adlı kateqoriya siyahısı yaradılır (statik, hər dəfə eynidir)
    val categories = (1..25).map { "sınaq $it" to "📝" }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sınaqlar", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())   // 25 kateqoriya ekrana sığmaya bilər, ona görə skroll aktivdir
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Kateqoriyaları 3-lük qruplara bölüb, hər qrupu bir sətirdə göstəririk
            categories.chunked(3).forEach { rowCategories ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowCategories.forEach { (name, _) ->
                        // "sınaq 7" -> 7 rəqəmini ayırırıq
                        val number = name.filter { it.isDigit() }.toIntOrNull() ?: 0
                        // Yalnız 1-3 üçün hazır sual dəsti var; 4-dən yuxarı yalnız ödənişdən sonra açılır
                        val isLocked = number >= 4 && !isUnlocked

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            // "Box" ilə kilid ikonunu kartın küncünə "yapışdırırıq" (TopEnd)
                            Box(contentAlignment = Alignment.TopEnd) {
                                Surface(
                                    onClick = {
                                        // Kilidli deyilsə - normal seçim; kilidlidirsə - ödəniş dialoqunu aç
                                        if (!isLocked) onCategorySelected(name) else onLockedClick()
                                    },
                                    modifier = Modifier.size(90.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    // Kilidli kartların rəngi solğun (surfaceVariant), açıq kartlar isə canlı rəngdə
                                    color = if (isLocked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                                    tonalElevation = if (isLocked) 0.dp else 4.dp,
                                    shadowElevation = if (isLocked) 0.dp else 4.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = number.toString(),
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                // Kilidli kartların üzərində kiçik qırmızı "kilid" ikonu göstərilir
                                if (isLocked) {
                                    Surface(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .offset(x = 6.dp, y = (-6).dp),   // kartın küncündən bir az kənara çıxsın deyə
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.error,
                                        shadowElevation = 4.dp
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = Color.White,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 4-cü sınaqdan yuxarı bir kateqoriyaya klikləyəndə açılan ödəniş pəncərəsi.
 *
 * AXIN:
 *  1) İstifadəçi telefon nömrəsini yazır, "Ödəniş et" düyməsinə basır
 *  2) viewModel.initiatePayment() Cloud Function-u çağırır, checkout linki gəlir
 *  3) Bu link Chrome Custom Tabs-da (aşağıdakı LaunchedEffect) açılır -
 *     kart məlumatları HEÇ VAXT bizim tətbiqin daxilində deyil, birbaşa
 *     Kapital Bank-ın öz səhifəsində daxil edilir (bu, təhlükəsizlik üçün vacibdir)
 *  4) Ödəniş təsdiqlənəndə, viewModel.isUnlocked avtomatik true olur (Firebase-dən
 *     canlı dinləmə sayəsində) və bu dialoq özü "Açıldı!" mesajına keçir
 */
@Composable
fun PaymentDialog(viewModel: TestViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // "LaunchedEffect(viewModel.checkoutUrl)" - checkoutUrl dəyişən HƏR DƏFƏ
    // (yəni yeni bir link gələndə) bu bloku bir dəfə işə salır.
    // Beləliklə, link gələn kimi avtomatik brauzer açılır.
    LaunchedEffect(viewModel.checkoutUrl) {
        viewModel.checkoutUrl?.let { url ->
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (viewModel.isUnlocked) "Açıldı!" else "Bütün sınaqları açın") },
        text = {
            Column {
                if (viewModel.isUnlocked) {
                    // Ödəniş artıq təsdiqlənib - sadəcə təbrik mesajı göstəririk
                    Text("Ödəniş təsdiqləndi, bütün sınaqlar artıq açıqdır.")
                } else {
                    // Ödəniş hələ edilməyib - telefon nömrəsi sahəsi və "Ödəniş et" düyməsi göstərilir
                    Text("4-cü sınaqdan etibarən bütün testlərə giriş üçün kart ilə ödəniş edin.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = viewModel.phoneNumber,
                        onValueChange = viewModel::updatePhoneNumber,
                        label = { Text("Telefon nömrəsi") },
                        singleLine = true,
                        enabled = !viewModel.isProcessingPayment,   // ödəniş gedərkən sahəni redaktə etməyə qoymuruq
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    // Ödəniş linki gözlənilərkən kiçik "gözlənilir" göstəricisi
                    if (viewModel.isProcessingPayment) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ödəniş gözlənilir...")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (viewModel.isUnlocked) {
                // Açılıb - "Bağla" düyməsi kifayətdir
                TextButton(onClick = onDismiss) { Text("Bağla") }
            } else {
                // Hələ açılmayıb - "Ödəniş et" düyməsi (telefon boş olduqca deaktivdir)
                Button(
                    onClick = viewModel::initiatePayment,
                    enabled = !viewModel.isProcessingPayment && viewModel.phoneNumber.isNotBlank()
                ) { Text("Ödəniş et") }
            }
        },
        dismissButton = {
            // Açılıbsa "Bağla" düyməsi artıq confirmButton-dadır, ikisini birdən göstərməyə ehtiyac yoxdur
            if (!viewModel.isUnlocked) {
                TextButton(onClick = onDismiss) { Text("Bağla") }
            }
        }
    )
}

/**
 * Admin paneli: gizli parol ilə daxil olan admin buradan yeni suallar əlavə edir.
 * Hər sual: kateqoriya, (istəyə bağlı) şəkil, mətn, 4 variant və düzgün cavab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    isLoading: Boolean,
    onBack: () -> Unit,
    onUpload: (String, String, List<String>, Int, Uri?) -> Unit
) {
    var category by remember { mutableStateOf("sınaq 1") }   // hansı sınağa sual əlavə olunur
    var text by remember { mutableStateOf("") }                // sualın mətni
    var option1 by remember { mutableStateOf("") }
    var option2 by remember { mutableStateOf("") }
    var option3 by remember { mutableStateOf("") }
    var option4 by remember { mutableStateOf("") }
    var correctIndex by remember { mutableIntStateOf(0) }       // hansı variant (0-3) düzgündür
    var imageUri by remember { mutableStateOf<Uri?>(null) }     // seçilmiş şəklin telefon daxilindəki ünvanı

    // Telefonun qalereyasından şəkil seçmək üçün "launcher" (Android-in hazır alətidir)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yeni Sual Əlavə Et", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 25 kateqoriyanı üfüqi sürüşdürülə bilən "çip" (chip) siyahısı kimi göstəririk
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Start
            ) {
                (1..25).forEach { i ->
                    val cat = "sınaq $i"
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Şəkil seçmə qutusu: klikləyəndə qalereya açılır, seçilibsə önizləmə göstərilir
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { launcher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Şəkil seç", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Sualın mətni üçün giriş sahəsi
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Sualın mətni") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text("Variantlar (Düzgün olanı seçin):", modifier = Modifier.align(Alignment.Start), fontWeight = FontWeight.Bold)

            // 4 variantı və onların "dəyişdirici" (setter) funksiyalarını siyahı şəklində
            // saxlamaq, aşağıdakı "forEachIndexed" ilə TƏKRARSIZ (eyni kodu 4 dəfə yazmadan) işləməyə imkan verir
            val options = listOf(option1, option2, option3, option4)
            val setters = listOf<(String) -> Unit>({ option1 = it }, { option2 = it }, { option3 = it }, { option4 = it })

            options.forEachIndexed { index, opt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Radio düyməsi: yalnız BİR variant "düzgün" seçilə bilər
                    RadioButton(selected = correctIndex == index, onClick = { correctIndex = index })
                    OutlinedTextField(
                        value = opt,
                        onValueChange = setters[index],
                        label = { Text("Variant ${index + 1}") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = { onUpload(category, text, listOf(option1, option2, option3, option4), correctIndex, imageUri) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                // Sual mətni və ən azı 2 variant doldurulmayınca, yüklənərkən isə düymə deaktivdir
                enabled = !isLoading && text.isNotBlank() && option1.isNotBlank() && option2.isNotBlank()
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Bazada Yadda Saxla", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * İmtahan zamanı göstərilən əsas sual ekranı.
 * Yuxarıda: sual nömrəsi, ümumi sayı, geri sayan taймer, tərəqqi zolağı (progress bar).
 * Aşağıda: sualın özü (və varsa şəkli) və cavab variantları.
 */
@Composable
fun QuestionScreen(
    question: Question,
    currentNum: Int,
    totalNum: Int,
    timeLeft: Int,
    onAnswer: (Int) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Sual $currentNum", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Text("Ümumi: $totalNum", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Vaxt qutusu: 30 saniyədən az qalanda rəngi qırmızıya (xəbərdarlıq rənginə) dəyişir
                    Surface(
                        color = if (timeLeft < 30) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = if (timeLeft < 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(8.dp))
                            // Saniyəni "dəqiqə:saniyə" formatına çeviririk (məs. 125 saniyə -> "02:05")
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:%02d", timeLeft / 60, timeLeft % 60),
                                fontWeight = FontWeight.Black,
                                color = if (timeLeft < 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Tərəqqi zolağı: neçənci sualda olduğumuzu vizual göstərir (məs. 5/25 -> 20% dolu)
                LinearProgressIndicator(
                    progress = { currentNum.toFloat() / totalNum },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sualın özü (və varsa şəkli) bir kart içində göstərilir
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Şəkil yalnız varsa göstərilir (admin sual əlavə edərkən şəkil seçməyə bilər)
                    if (question.imageUrl != null) {
                        AsyncImage(
                            model = question.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(24.dp))
                    }

                    Text(
                        text = question.text,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Cavab variantları: hər birinə klikləmək dərhal "onAnswer(index)" çağırır -
            // yəni "Təsdiqlə" düyməsi yoxdur, seçim edən kimi növbəti suala keçir
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.options.forEachIndexed { index, option ->
                    Surface(
                        onClick = { onAnswer(index) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.padding(20.dp),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * İmtahan bitəndə göstərilən nəticə ekranı.
 * Uğur şərti: xal >= ümumi sualların yarısı. Buna görə rənglər (yaşıl/qırmızı)
 * və ikon (✓/✗) dəyişir.
 */
@Composable
fun ResultScreen(userInfo: UserInfo?, score: Int, total: Int, onRestart: () -> Unit) {
    val isSuccess = score >= total / 2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Uğurlu olanda yaşılımtıl, uğursuz olanda qırmızımtıl fon
                Brush.verticalGradient(
                    colors = if (isSuccess) listOf(Color(0xFFE8F5E9), Color.White) else listOf(Color(0xFFFFEBEE), Color.White)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = if (isSuccess) "Təbriklər!" else "Yenidən Cəhd Et!",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
            Text(
                text = "${userInfo?.firstName} ${userInfo?.lastName}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(48.dp))

            // Xal kartı: böyük rəqəmlə "5 / 25" formatında nəticə
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Nəticəniz", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "$score / $total",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onRestart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Yenidən Başla", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


// ==========================================================================
// AŞAĞIDAKILAR "PREVIEW" (ÖNİZLƏMƏ) FUNKSİYALARIDIR.
// Bunlar tətbiqi telefonda İŞƏ SALMADAN, Android Studio-nun içindəki
// "Design" panelində ekranlara baxmaq üçündür. Real istifadəçi bunları görmür,
// bu, YALNIZ inkişaf zamanı (developer üçün) əlverişlilikdir.
// ==========================================================================

@Preview(showBackground = true)
@Composable
fun RegistrationPreview() {
    MyApplicationTheme {
        RegistrationScreen(onRegister = { _, _ -> }, onAdminClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun CategorySelectionPreview() {
    MyApplicationTheme {
        // "isUnlocked = false" - önizləmədə kilidlərin necə göründüyünü görmək üçün
        CategorySelectionScreen(isUnlocked = false, onCategorySelected = {}, onLockedClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun AdminPreview() {
    MyApplicationTheme {
        AdminScreen(
            isLoading = false,
            onBack = {},
            onUpload = { _, _, _, _, _ -> }
        )
    }
}
