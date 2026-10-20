package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.LessonBlue
import com.example.myapplication.ui.theme.LessonBlueContainer
import com.example.myapplication.ui.theme.LessonOrange
import com.example.myapplication.ui.theme.LessonOrangeContainer
import com.example.myapplication.ui.theme.LessonGreen
import com.example.myapplication.ui.theme.LessonGreenContainer
import com.example.myapplication.ui.theme.LessonPurple
import com.example.myapplication.ui.theme.LessonPurpleContainer
import com.example.myapplication.ui.theme.LessonPink
import com.example.myapplication.ui.theme.LessonPinkContainer
import com.example.myapplication.ui.theme.LessonTeal
import com.example.myapplication.ui.theme.LessonTealContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

// ==========================================================================
// BU FAYL TƏTBIQIN BÜTÜN EKRANLARINI (Compose UI) SAXLAYIR.
// Burada YALNIZ görünüş var - hansı düymə hara basılır, nə rəngdədir və s.
// Məntiq (server sorğuları, hesablama, taймer) TestViewModel.kt-dədir, bu fayl
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
    // sualının cavabıdır - serverə aid deyil.
    var showPaymentDialog by remember { mutableStateOf(false) }

    // Hər ekran keçidində qısa (400ms) yüklənmə göstəricisi görünsün deyə.
    // "LaunchedEffect(viewModel.screenState)" - screenState HƏR DƏYİŞƏNDƏ
    // (yəni hər ekran keçidində) bu blok yenidən işə düşür.
    //
    // DİQQƏT: bu göstərici AYRICA bir overlay kimi (aşağıda AnimatedVisibility
    // ilə) göstərilir, ƏSAS ekranı AnimatedContent-dən çıxarıb yenidən qurmur.
    // Əvvəlki versiya bunu bir edirdi (Pair(isTransitioning, state) ilə) və
    // hər keçiddə bütün ekranı təzədən çəkdiyi üçün zəif cihazlarda donma
    // hiss olunurdu - indi əsas ekran toxunulmadan qalır, yalnız üstündən
    // yüngül, tam-ekran fade overlay keçir.
    var isTransitioning by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.screenState) {
        isTransitioning = true
        delay(TRANSITION_DURATION_MS.milliseconds)
        isTransitioning = false
    }

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
                ScreenState.RoleSelection -> RoleSelectionScreen(
                    onStudentClick = { viewModel.screenState = ScreenState.PhoneEntry },
                    onAdminVerify = viewModel::adminLogin,
                    onTeacherClick = { viewModel.screenState = ScreenState.Admin }
                )
                ScreenState.PhoneEntry -> PhoneEntryScreen(
                    phoneNumber = viewModel.phoneNumber,
                    isCheckingPhone = viewModel.isCheckingPhone,
                    isReturningUser = viewModel.isReturningUser,
                    isLoggingInWithPassword = viewModel.isLoggingInWithPassword,
                    onPhoneChange = viewModel::updatePhoneNumber,
                    onContinue = viewModel::checkPhoneAndProceed,
                    onPasswordLogin = viewModel::loginWithPassword,
                    onAdminVerify = viewModel::adminLogin,
                    onAdminClick = { viewModel.screenState = ScreenState.Admin },
                    onBack = { viewModel.screenState = ScreenState.RoleSelection }
                )
                ScreenState.NameEntry -> NameEntryScreen(
                    isRegistering = viewModel.isRegistering,
                    onRegister = viewModel::register,
                    onBack = { viewModel.screenState = ScreenState.RoleSelection }
                )
                ScreenState.CategorySelection -> CategorySelectionScreen(
                    categories = viewModel.categories,
                    isUnlocked = viewModel.isUnlocked,
                    onCategorySelected = viewModel::selectCategory,
                    onLockedClick = { showPaymentDialog = true },   // kilidli sınağa klik -> ödəniş dialoqu aç
                    onAdminVerify = viewModel::adminLogin,
                    onAdminClick = { viewModel.screenState = ScreenState.Admin },
                    onBack = { viewModel.screenState = ScreenState.RoleSelection }
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
                            onAnswer = viewModel::answerQuestion,
                            onExit = viewModel::exitTest
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
                    categories = viewModel.categories,
                    categoryQuestions = viewModel.selectedCategoryQuestions,
                    onAddCategory = viewModel::addNewCategory,
                    onCategoryChange = viewModel::refreshSelectedCategoryQuestions,
                    onDeleteCategory = viewModel::deleteCategory,
                    onDeleteQuestion = viewModel::deleteQuestion,
                    onBack = {
                        // Admin panelinə hardan gəlinibsə (giriş edilməzdən əvvəl və ya sonra),
                        // müvafiq ekrana qayıtsın
                        viewModel.screenState =
                            if (viewModel.userInfo != null) ScreenState.CategorySelection
                            else ScreenState.RoleSelection
                        viewModel.adminLogout()
                    },
                    onUpload = viewModel::uploadQuestion,
                    onManualUnlock = viewModel::manualUnlock,
                    onDeviceReset = viewModel::resetDevice,
                    onBulkUpload = viewModel::uploadQuestionsBulk
                )
                ScreenState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
        }

        // Ekran keçidi overlay-i: əsas ekranı yenidən qurmadan, sadəcə
        // üstündən yüngül fade ilə keçir - "donma" hiss olunmasın deyə.
        AnimatedVisibility(
            visible = isTransitioning,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TransitionLoadingScreen()
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

// Ekran keçidi overlay-inin nə qədər görünəcəyi (millisaniyə). Proqres zolağı
// da elə bu müddətdə %0-dan %100-ə dolur.
const val TRANSITION_DURATION_MS = 500L

/**
 * Ekranlar arasında keçid zamanı (hər dəfə) qısaca görünən yüklənmə overlay-i.
 * Faktiki gözləmə olmasa belə göstərilir ki, hər keçid "canlı" hiss olunsun.
 *
 * DÜZ (xətti) proqres zolağı %0-dan %100-ə qədər dolur. Bu, ayrıca, yüngül
 * bir overlay kimi göstərildiyi üçün (əsas ekranı yenidən qurmadan - bax:
 * TestApp-dakı AnimatedVisibility) zəif cihazlarda da hamar işləməlidir.
 */
@Composable
fun TransitionLoadingScreen() {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = TRANSITION_DURATION_MS.toInt(), easing = LinearEasing)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${(progress.value * 100).toInt()}%",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Admin parolunu soruşan dialoq. Həm PhoneEntryScreen-də (gizli ikon), həm də
 * CategorySelectionScreen-də (artıq giriş etmiş istifadəçilər üçün) istifadə
 * olunur ki, admin panelinə giriş sessiyanın vəziyyətindən asılı olmayaraq
 * həmişə mümkün olsun.
 *
 * "onVerify" - parolu TestViewModel.adminLogin() vasitəsilə serverə göndərir;
 * parol yalnız serverdə (ADMIN_PASSWORD) yoxlanılır, tətbiqin içində saxlanılmır.
 */
@Composable
fun AdminLoginDialog(onDismiss: () -> Unit, onVerify: (String, (Boolean) -> Unit) -> Unit, onSuccess: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }   // yanlış parol yazılıbsa true olur
    var isChecking by remember { mutableStateOf(false) } // serverdən cavab gözlənilərkən true

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    enabled = !isChecking,
                    isError = isError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                    isChecking = true
                    onVerify(password) { success ->
                        isChecking = false
                        if (success) onSuccess() else isError = true
                    }
                },
                enabled = !isChecking,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isChecking) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Daxil Ol")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isChecking) {
                Text("Ləğv Et")
            }
        }
    )
}

/**
 * Giriş ekranlarının (RoleSelection/PhoneEntry/NameEntry) ortaq fonu: brend
 * konteyner rənğindən səthə doğru gradient, üzərində çox yavaş (14 saniyəlik),
 * aşağı-kontrastlı "nəfəs alan" bir işıq ləkəsi ilə.
 *
 * DİQQƏT: bu, rəsmi bank/dövlət portallarında tez-tez görülən "sakit ambient
 * hərəkət" nümunəsidir - üzən ikon/maskot kimi "playful" elementlər YOXDUR,
 * çünki tədqiqat göstərir ki, bu cür hərəkətlər rəsmi/etibarlı hiss əvəzinə
 * oyun tətbiqi hissi yaradır. Hərəkət HƏMİŞƏ fonun özündədir, əsas kart
 * (üzərindəki məzmun) tərpənmədən, yüksək kontrastda qalır - diqqəti
 * yayındırmasın və oxunaqlılığı pozmasın deyə.
 */
@Composable
private fun BrandBackgroundBox(content: @Composable BoxScope.() -> Unit) {
    val drift = rememberInfiniteTransition(label = "brandBgDrift")
    val t by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "brandBgDriftT"
    )
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
                )
            )
            .drawBehind {
                // İşıq ləkəsi ekranın yuxarı hissəsində sol-sağ arasında asta sürüşür (t: 0->1->0)
                val center = Offset(size.width * (0.25f + 0.5f * t), size.height * 0.18f)
                val radius = size.maxDimension * 0.55f
                drawCircle(
                    brush = Brush.radialGradient(colors = listOf(highlight, Color.Transparent), center = center, radius = radius),
                    radius = radius,
                    center = center
                )
            },
        content = content
    )
}

/**
 * Tətbiqin ƏSL İLK ekranı: istifadəçi "Şagird" və ya "Müəllim" olduğunu seçir.
 *  - "Şagird" -> telefon+SMS girişinə keçir (PhoneEntryScreen)
 *  - "Müəllim" -> admin parolunu soruşur, düzgündürsə birbaşa Admin panelinə keçir
 */
@Composable
fun RoleSelectionScreen(onStudentClick: () -> Unit, onAdminVerify: (String, (Boolean) -> Unit) -> Unit, onTeacherClick: () -> Unit) {
    var showAdminLogin by remember { mutableStateOf(false) }

    if (showAdminLogin) {
        AdminLoginDialog(
            onDismiss = { showAdminLogin = false },
            onVerify = onAdminVerify,
            onSuccess = {
                showAdminLogin = false
                onTeacherClick()
            }
        )
    }

    BrandBackgroundBox {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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
                        text = "Kim olaraq daxil olursunuz?",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onStudentClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Şagird", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showAdminLogin = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.School, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Müəllim", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.prepared_by_photo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Prepared by Ismayil Garayev",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Şagird girişi: telefon nömrəsi daxil edilir.
 * Sol-yuxarı küncdəki ox düyməsi (və telefonun öz "geri" düyməsi/jesti) rol
 * seçiminə (RoleSelectionScreen) qaytarır. Sağ-alt küncdəki gizli (şəffaf)
 * parametr ikonu isə admin girişini açır (ehtiyat yol).
 */
@Composable
fun PhoneEntryScreen(
    phoneNumber: String,
    isCheckingPhone: Boolean,
    isReturningUser: Boolean,
    isLoggingInWithPassword: Boolean,
    onPhoneChange: (String) -> Unit,
    onContinue: () -> Unit,
    onPasswordLogin: (String) -> Unit,
    onAdminVerify: (String, (Boolean) -> Unit) -> Unit,
    onAdminClick: () -> Unit,
    onBack: () -> Unit
) {
    var showAdminLogin by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val isBusy = isCheckingPhone || isLoggingInWithPassword

    // Telefonun fiziki/jest "geri" hərəkəti də rol seçiminə qaytarsın (tətbiqdən
    // birbaşa çıxmaq əvəzinə)
    BackHandler(onBack = onBack)

    if (showAdminLogin) {
        AdminLoginDialog(
            onDismiss = { showAdminLogin = false },
            onVerify = onAdminVerify,
            onSuccess = {
                showAdminLogin = false
                onAdminClick()
            }
        )
    }

    // Ekranın əsas gövdəsi: gradient fon + ortada giriş kartı
    BrandBackgroundBox {
        // Rol seçiminə qayıtma düyməsi
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Geri",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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
                        text = if (isReturningUser) "Nömrənizi və parolunuzu daxil edin"
                               else "Davam etmək üçün telefon nömrənizi daxil edin",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = onPhoneChange,
                        label = { Text("Nömrə") },
                        placeholder = { Text("501234567") },
                        prefix = { Text("+994 ") },
                        singleLine = true,
                        enabled = !isBusy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Bu nömrə artıq qeydiyyatlıdırsa, SMS əvəzinə parol sahəsi göstərilir
                    if (isReturningUser) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Parol") },
                            singleLine = true,
                            enabled = !isBusy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { if (isReturningUser) onPasswordLogin(password) else onContinue() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isBusy && phoneNumber.filter { it.isDigit() }.length >= 9 &&
                            (!isReturningUser || password.isNotBlank())
                    ) {
                        if (isBusy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Text(if (isReturningUser) "Daxil ol" else "Davam et", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
 * YALNIZ YENİ hesablar üçün göstərilən ad-soyad + parol daxil etmə ekranı.
 * Qayıdan istifadəçilər bu ekranı görmür, çünki ad-soyadları "users/<telefon>"
 * düyünündən avtomatik bərpa olunur.
 */
@Composable
fun NameEntryScreen(isRegistering: Boolean, onRegister: (String, String, String) -> Unit, onBack: () -> Unit) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Telefonun fiziki/jest "geri" hərəkəti də rol seçiminə qaytarsın
    BackHandler(onBack = onBack)

    BrandBackgroundBox {
        // Rol seçiminə qayıtma düyməsi (digər ekranlarla eyni yerdə/görünüşdə)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Geri",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Tanış olaq",
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
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("Ad") },
                        enabled = !isRegistering,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Soyad") },
                        enabled = !isRegistering,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Parol təyin edin") },
                        singleLine = true,
                        enabled = !isRegistering,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        text = "Növbəti girişlərdə bu parolu istifadə edəcəksiniz (ən azı 4 simvol)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { onRegister(firstName, lastName, password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        // Ad, soyad boş olduqca və ya parol qısa olduqca düymə deaktiv qalır
                        enabled = !isRegistering && firstName.isNotBlank() && lastName.isNotBlank() && password.length >= 4
                    ) {
                        if (isRegistering) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Text("İmtahana Başla", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Kateqoriya (sınaq) seçimi ekranı: serverdəki sınaqları 3-lük sıralarla
 * göstərir. 4-cü sınaqdan yuxarı, ödəniş edilməyibsə, kilidli olur. Sınaqların
 * sayı sabit deyil - admin panelindən artırıla bilər (bax: TestViewModel.addNewCategory).
 *
 * DİQQƏT: Bu funksiya bilərəkdən TestViewModel-i BİRBAŞA qəbul ETMİR -
 * yalnız sadə parametrlər (siyahı, Boolean, funksiyalar) alır. Bu, iki səbəbə görədir:
 *  1) Android Studio-nun preview (canlı önizləmə) paneli serverə qoşula
 *     bilmir, ona görə TestViewModel-i birbaşa versək önizləmə xəta verir.
 *  2) Composable-ı ViewModel-dən ayırmaq kodu daha təmiz və test edilə bilən edir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectionScreen(
    categories: List<Category>,
    isUnlocked: Boolean,
    onCategorySelected: (Int) -> Unit,
    onLockedClick: () -> Unit,
    onAdminVerify: (String, (Boolean) -> Unit) -> Unit,
    onAdminClick: () -> Unit,
    onBack: () -> Unit
) {
    var showAdminLogin by remember { mutableStateOf(false) }

    // Telefonun fiziki/jest "geri" hərəkəti də rol seçiminə qaytarsın
    BackHandler(onBack = onBack)

    if (showAdminLogin) {
        AdminLoginDialog(
            onDismiss = { showAdminLogin = false },
            onVerify = onAdminVerify,
            onSuccess = {
                showAdminLogin = false
                onAdminClick()
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sınaqlar", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                // Rol seçiminə qayıtma (digər ekranlarla eyni görünüşdə)
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                // Artıq giriş etmiş istifadəçilər PhoneEntry ekranını görmədiyi üçün,
                // admin panelinə buradan da (gizli olmayan, kiçik ikonla) girmək mümkündür
                actions = {
                    IconButton(onClick = { showAdminLogin = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Admin",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        // "LazyVerticalGrid" (əvvəlki 3-lük "chunked" Row-lardan fərqli olaraq) hər
        // kartı EYNİ enli sütuna yerləşdirir - sayı 3-ə bölünmürsə (məs. 25-in son
        // sətrində 1 kart qalanda), köhnə "SpaceEvenly" Row bu tək kartı sətrin
        // ortasına "sərirdi" və yuxarıdakı sətirlərlə düzülmürdü. İndi hər kart öz
        // sütununda qalır, son sətir natamam olsa belə düzgün görünür.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(categories, key = { it.number }) { category ->
                val number = category.number
                // 1-3 pulsuzdur; 4-dən yuxarı yalnız ödənişdən sonra açılır (server də yoxlayır)
                val isLocked = number >= 4 && !isUnlocked
                // Hər açıq kartın öz fənn ikonu+rəngi olsun deyə (dövri) - "dərs rəfi"
                // hissi versin, hamısı eyni görünən kvadrat olmasın
                val accent = lessonAccents[(number - 1).mod(lessonAccents.size)]

                CategoryCard(
                    number = number,
                    // Admin bu sınağa xüsusi ad veribsə (məs. "Fizika") onu göstəririk,
                    // yoxdursa defolt "sınaq N" adı qalır
                    label = category.displayName,
                    isLocked = isLocked,
                    accent = accent,
                    onClick = {
                        // Kilidli deyilsə - normal seçim; kilidlidirsə - ödəniş dialoqunu aç
                        if (!isLocked) onCategorySelected(number) else onLockedClick()
                    }
                )
            }
        }
    }
}

/** Bir dərs ikonu + ona uyğun açıq fon/tünd mətn rəngi cütü. */
private data class LessonAccent(val icon: ImageVector, val container: Color, val onContainer: Color)

// Açıq kartlar bu siyahıdan dövri (number % size) olaraq ikon+rəng götürür -
// bax: ui/theme/Color.kt-də "Dərs rəfi aksentləri" qeydi
private val lessonAccents = listOf(
    LessonAccent(Icons.Default.Book, LessonBlueContainer, LessonBlue),
    LessonAccent(Icons.Default.Edit, LessonOrangeContainer, LessonOrange),
    LessonAccent(Icons.Default.Science, LessonGreenContainer, LessonGreen),
    LessonAccent(Icons.Default.Public, LessonPurpleContainer, LessonPurple),
    LessonAccent(Icons.Default.Calculate, LessonPinkContainer, LessonPink),
    LessonAccent(Icons.Default.Palette, LessonTealContainer, LessonTeal)
)

/**
 * CategorySelectionScreen-dəki grid-in tək bir xücrəsi: künclü kart içində fənn
 * ikonu + nömrə, altında ad, kilidlidirsə kiçik qırmızı kilid nişanı ilə.
 * Ayrıca composable-a çıxarılıb ki, LazyVerticalGrid-in "items" bloku sadə qalsın.
 */
@Composable
private fun CategoryCard(number: Int, label: String, isLocked: Boolean, accent: LessonAccent, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        // "Box" ilə kilid ikonunu kartın küncünə "yapışdırırıq" (TopEnd)
        Box(contentAlignment = Alignment.TopEnd) {
            Surface(
                onClick = onClick,
                modifier = Modifier.size(90.dp),
                shape = RoundedCornerShape(16.dp),
                // Kilidli kartların rəngi solğun (surfaceVariant), açıq kartlar isə öz fənn rəngində
                color = if (isLocked) MaterialTheme.colorScheme.surfaceVariant else accent.container,
                tonalElevation = if (isLocked) 0.dp else 4.dp,
                shadowElevation = if (isLocked) 0.dp else 4.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!isLocked) {
                        Icon(
                            imageVector = accent.icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = accent.onContainer
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        text = number.toString(),
                        fontSize = if (isLocked) 32.sp else 22.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else accent.onContainer
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
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.width(90.dp)
        )
    }
}

// Kart-karta köçürmə üçün göstərilən məlumatlar "local.properties" faylından gəlir
// (PAYMENT_CARD_NUMBER, PAYMENT_AMOUNT_AZN) - admin parolu kimi bu fayl da git-ə
// daxil edilmir, ona görə real kart nömrəsi kod deposunda (GitHub-da) görünmür.
private const val PAYMENT_CARD_NUMBER = BuildConfig.PAYMENT_CARD_NUMBER
private const val PAYMENT_AMOUNT_AZN = BuildConfig.PAYMENT_AMOUNT_AZN

/**
 * 4-cü sınaqdan yuxarı bir kateqoriyaya klikləyəndə açılan pəncərə.
 *
 * AXIN (bank inteqrasiyası OLMADAN, sadə kart-karta köçürmə ilə):
 *  1) İstifadəçiyə kart nömrəsi və məbləğ göstərilir (telefon nömrəsi artıq
 *     giriş zamanı təsdiqləndiyi üçün yenidən soruşulmur)
 *  2) İstifadəçi köçürməni öz bank tətbiqi ilə edir (bizim tətbiqin xaricində)
 *  3) Admin (Admin panelindən) köçürməni gördükdən sonra əl ilə həmin nömrəni açır
 *  4) Bu pəncərə açıq qaldığı müddətdə status hər 15 saniyədən bir serverdən
 *     yenilənir (bax: TestViewModel init) - açılan kimi "Açıldı!" mesajına keçir
 */
@Composable
fun PaymentDialog(viewModel: TestViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (viewModel.isUnlocked) "Açıldı!" else "Bütün sınaqları açın") },
        text = {
            Column {
                if (viewModel.isUnlocked) {
                    // Admin artıq açıb - sadəcə təbrik mesajı göstəririk
                    Text("Ödəniş təsdiqləndi, bütün sınaqlar artıq açıqdır.")
                } else {
                    // Hələ açılmayıb - kart nömrəsi, məbləğ və təlimat göstərilir
                    Text("4-cü sınaqdan etibarən bütün testlərə giriş ödənişlidir.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Kart nömrəsi:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                PAYMENT_CARD_NUMBER,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Məbləğ: $PAYMENT_AMOUNT_AZN AZN",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        // "accountPhone" ölkə kodunu (994) artıq özündə saxlayır - yalnız "+" əlavə edirik
                        text = "Köçürmə edərkən hesabınıza bağlı telefon nömrəsini (+${viewModel.accountPhone}) admininə bildirin.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ödəniş təsdiqlənəndə bu pəncərə avtomatik yenilənəcək.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Bağla") }
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
    categories: List<Category>,
    categoryQuestions: List<Question>,
    onAddCategory: (String, (Int) -> Unit) -> Unit,
    onCategoryChange: (Int) -> Unit,
    onDeleteCategory: (Int) -> Unit,
    onDeleteQuestion: (Int, Long) -> Unit,
    onBack: () -> Unit,
    onUpload: (Int, String, List<String>, Int, Uri?, () -> Unit) -> Unit,
    onManualUnlock: (String, () -> Unit) -> Unit,
    onDeviceReset: (String, () -> Unit) -> Unit,
    onBulkUpload: (Int, String, () -> Unit) -> Unit
) {
    var category by remember { mutableIntStateOf(1) }   // hansı sınağa (nömrə) sual əlavə olunur
    var text by remember { mutableStateOf("") }                // sualın mətni
    var option1 by remember { mutableStateOf("") }
    var option2 by remember { mutableStateOf("") }
    var option3 by remember { mutableStateOf("") }
    var option4 by remember { mutableStateOf("") }
    var correctIndex by remember { mutableIntStateOf(0) }       // hansı variant (0-3) düzgündür
    var imageUri by remember { mutableStateOf<Uri?>(null) }     // seçilmiş şəklin telefon daxilindəki ünvanı
    var showDeleteCategoryConfirm by remember { mutableStateOf(false) }  // "Sınağı sil" təsdiq dialoqu
    var showAddCategoryDialog by remember { mutableStateOf(false) }      // "Yeni sınaq" ad soruşma dialoqu
    // Mövcud sual siyahısı default QAPALI - uzun siyahı admini formdan "scroll
    // məsafəsi" ilə ayırmasın, lazım olanda bir kliklə aça bilsin
    var showQuestionList by remember { mutableStateOf(false) }

    // Hər sual saxlanıldıqda göstərilən qısa təsdiq mesajı (bax: aşağıdakı Scaffold-un snackbarHost-u)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Klaviaturada "Next" düyməsi ilə sual->variant1->variant2->variant3->variant4
    // arasında toxunmadan keçid etmək üçün - admin əlini ekrandan qaldırmadan
    // bütün sualı yaza bilsin
    val focusManager = LocalFocusManager.current
    val option1Focus = remember { FocusRequester() }
    val option2Focus = remember { FocusRequester() }
    val option3Focus = remember { FocusRequester() }
    val option4Focus = remember { FocusRequester() }
    val optionFocusRequesters = remember { listOf(option1Focus, option2Focus, option3Focus, option4Focus) }

    // Seçilmiş sınağın (varsa) admin tərəfindən verilmiş öz adı
    val categoryDisplayName = categories.find { it.number == category }?.displayName ?: "sınaq $category"

    // Telefonun fiziki/jest "geri" hərəkəti də əvvəlki ekrana qaytarsın
    BackHandler(onBack = onBack)

    if (showAddCategoryDialog) {
        var newCategoryName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Yeni sınaq") },
            text = {
                Column {
                    Text(
                        "Sınağa ad verə bilərsiniz (məs. \"Fizika\"). Boş buraxsanız, sadəcə nömrə ilə adlanacaq.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Sınağın adı (istəyə bağlı)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Nömrəni server təyin edir - yaradılan kimi o sınağa keçirik
                    onAddCategory(newCategoryName) { category = it }
                    showAddCategoryDialog = false
                }) { Text("Əlavə et") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) { Text("Ləğv et") }
            }
        )
    }

    if (showDeleteCategoryConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteCategoryConfirm = false },
            title = { Text("Sınağı sil") },
            text = { Text("\"$categoryDisplayName\" sınağı və içindəki BÜTÜN suallar həmişəlik silinəcək. Davam edilsin?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCategory(category)
                    showDeleteCategoryConfirm = false
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCategoryConfirm = false }) { Text("Ləğv et") }
            }
        )
    }

    // Seçilmiş kateqoriya dəyişəndə VƏ hər sual yükləməsi bitəndə (isLoading
    // true-dan false-a keçəndə) həmin sınağın sual siyahısını təzələyirik ki,
    // admin sınağın İÇİNƏ baxıb, elə oradan yeni sual əlavə edə bilsin
    LaunchedEffect(category, isLoading) {
        if (!isLoading) onCategoryChange(category)
    }

    // Seçili sınaq mövcud deyilsə (silinib və ya siyahı yenicə yüklənib), ilk
    // mövcud sınağa keçirik ki, olmayan bir sınağa sual yüklənməsin
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && categories.none { it.number == category }) {
            category = categories.first().number
        }
    }

    // Telefonun qalereyasından şəkil seçmək üçün "launcher" (Android-in hazır alətidir)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }

    // Admin panelində 3 ayrı vəzifə var (sualları idarə et, toplu yüklə, ödəniş/cihaz) -
    // əvvəllər bunlar HAMISI bir-birinin altında, sonsuz bir scroll şəklində idi.
    // İndi "TabRow" ilə 3 ayrı bölməyə bölünüb ki, admin axtardığını tez tapsın,
    // hər dəfə lazım olmayan hissələri scroll edib keçməyə məcbur olmasın.
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Suallar", "Toplu yüklə", "Ödəniş və cihaz")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Paneli", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Bütün mövcud kateqoriyaları üfüqi sürüşdürülə bilən "çip" (chip) siyahısı kimi
            // göstəririk - hansı tab-da olursa olsun HƏMİŞƏ görünür, çünki hər üç bölmə də
            // (sual əlavə et, toplu yüklə) elə bu seçilmiş kateqoriyaya tətbiq olunur
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = category == cat.number,
                        onClick = { category = cat.number },
                        label = { Text(cat.displayName) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                AssistChip(
                    onClick = { showAddCategoryDialog = true },
                    label = { Text("+ Yeni sınaq") },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 13.sp) }
                    )
                }
            }

            // Tab-lar arasında keçid yumşaq (fade+slide) olsun deyə - hər tab-ın
            // içəriyi öz skroll vəziyyətini saxlayır (fərqli "remember" blokları)
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "AdminTabTransition",
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    0 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Hazırkı kateqoriyada olan sualların sayı (solda, klikləyərək siyahını
                        // aç/bağla) və bütün sınağı silmək düyməsi (sağda) - 25 tövsiyə olunan
                        // hədəfdir, sərt limit deyil
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showQuestionList = !showQuestionList },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (showQuestionList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "\"$categoryDisplayName\" sınağının içindəki suallar (${categoryQuestions.size} / tövsiyə: 25)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (categoryQuestions.size >= 25) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { showDeleteCategoryConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sınağı sil", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            }
                        }

                        // Sınağın İÇİNDƏKİ mövcud sualların siyahısı - default qapalı, admin
                        // yuxarıdakı sətrə klikləyəndə açılır. Bu, formu scroll məsafəsindən
                        // yaxınlaştırır - uzun siyahı hər dəfə yolu kəsmir
                        AnimatedVisibility(visible = showQuestionList) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                if (categoryQuestions.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.QuestionAnswer,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Bu sınaqda hələ sual yoxdur.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 260.dp)
                                            .verticalScroll(rememberScrollState())
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp)
                                    ) {
                                        categoryQuestions.forEachIndexed { index, q ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${index + 1}. ${q.text}",
                                                    fontSize = 13.sp,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(vertical = 4.dp)
                                                )
                                                IconButton(
                                                    onClick = { onDeleteQuestion(category, q.id) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Sualı sil",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(20.dp))

                        Text(
                            text = "Yeni sual əlavə et",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.height(16.dp))

                        // Sualın mətni üçün giriş sahəsi - formda İLK sahə, çünki hər sual üçün
                        // məcburidir (şəkil əksinə istəyə bağlıdır, ona görə aşağıda qalır).
                        // "Next" düyməsi klaviaturada birbaşa 1-ci variantın sahəsinə keçir.
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Sualın mətni") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { option1Focus.requestFocus() })
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
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(optionFocusRequesters[index]),
                                    shape = RoundedCornerShape(12.dp),
                                    // Variant 1-3-də "Next" növbəti variantın sahəsinə keçir, son
                                    // variantda "Done" klaviaturayı bağlayır (artıq giriş qalmayıb)
                                    keyboardOptions = KeyboardOptions(imeAction = if (index < options.lastIndex) ImeAction.Next else ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onNext = { optionFocusRequesters.getOrNull(index + 1)?.requestFocus() },
                                        onDone = { focusManager.clearFocus() }
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Şəkil seçmə qutusu: İSTƏYƏ BAĞLI olduğu üçün formda SON yerdə -
                        // klikləyəndə qalereya açılır, seçilibsə önizləmə + silmə (x) düyməsi göstərilir
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { launcher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUri != null) {
                                AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                IconButton(
                                    onClick = { imageUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(28.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Şəkli sil", modifier = Modifier.size(16.dp))
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Image, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("Şəkil seç (istəyə bağlı)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(Modifier.height(32.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onUpload(category, text, listOf(option1, option2, option3, option4), correctIndex, imageUri) {
                                    // Uğurla saxlanıldı: formu təmizləyirik ki, admin dərhal
                                    // növbəti sualı yaza bilsin, əlavə mesajla da təsdiq edirik
                                    text = ""
                                    option1 = ""
                                    option2 = ""
                                    option3 = ""
                                    option4 = ""
                                    correctIndex = 0
                                    imageUri = null
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Sual əlavə edildi.") }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            // Sual mətni və ən azı 2 variant doldurulmayınca, yüklənərkən isə düymə deaktivdir
                            enabled = !isLoading && text.isNotBlank() && option1.isNotBlank() && option2.isNotBlank()
                        ) {
                            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            else Text("Bazada Yadda Saxla", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(24.dp))
                    }

                    1 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Bir dəfəyə çoxlu sual əlavə etmək bölməsi: yuxarıda (çiplərdən)
                        // seçilmiş kateqoriyaya tətbiq olunur.
                        BulkUploadSection(
                            categoryLabel = categoryDisplayName,
                            onBulkUpload = { rawText, clearForm ->
                                onBulkUpload(category, rawText) {
                                    clearForm()
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Suallar əlavə edildi.") }
                                }
                            }
                        )
                        Spacer(Modifier.height(24.dp))
                    }

                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Ödənişi əl ilə açma bölməsi: şagird kart-karta köçürməni etdikdən
                        // sonra, admin bura həmin telefon nömrəsini yazıb "Aç" düyməsinə basır -
                        // bank inteqrasiyası olmadan, serverdə həmin nömrə açıq qeyd olunur.
                        ManualUnlockSection(onUnlock = onManualUnlock)

                        Spacer(Modifier.height(32.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(24.dp))

                        // Cihaz sıfırlama bölməsi: hər hesab ilk aktivləşdiyi telefona
                        // "bağlanır" (paylaşılmanın qarşısını almaq üçün) - şagirdin telefonu
                        // dəyişəndə admin bura nömrəni yazıb sıfırlayır ki, yeni telefon
                        // həmin hesaba yenidən bağlana bilsin.
                        DeviceResetSection(onReset = onDeviceReset)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/**
 * Admin panelinin ortasında görünən, bir dəfəyə çoxlu sual əlavə etmə bölməsi.
 * Format: hər sual boş sətirlə ayrılır, sualın altındakı sətirlər variantlardır,
 * düzgün variantın əvvəlinə "*" qoyulur.
 */
@Composable
private fun BulkUploadSection(categoryLabel: String, onBulkUpload: (String, () -> Unit) -> Unit) {
    var bulkText by remember { mutableStateOf("") }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Toplu sual əlavə et",
            modifier = Modifier.align(Alignment.Start),
            fontWeight = FontWeight.Bold
        )
        Text(
            "Hər sualı boş sətirlə ayırın. Sualın altındakı sətirlər variantlardır, düzgün olanın əvvəlinə \"*\" qoyun. Yuxarıda seçili kateqoriyaya (\"$categoryLabel\") əlavə olunacaq.",
            modifier = Modifier.align(Alignment.Start),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                "Nümunə:\nAzərbaycanın paytaxtı hansıdır?\nGəncə\n*Bakı\nŞəki\nQuba",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = bulkText,
            onValueChange = { bulkText = it },
            label = { Text("Suallar") },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onBulkUpload(bulkText) { bulkText = "" } },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = bulkText.isNotBlank()
        ) {
            Text("Toplu Yadda Saxla", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Admin panelinin altında görünən, telefon nömrəsi ilə əl ilə açılış bölməsi.
 * Ayrıca composable-a çıxarılıb ki, AdminScreen-in özü həddindən artıq
 * böyüməsin və bu hissə tək başına oxuna bilsin.
 */
@Composable
private fun ManualUnlockSection(onUnlock: (String, () -> Unit) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<String?>(null) }   // "X açıldı" mesajı üçün

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Ödənişi əl ilə aç",
            modifier = Modifier.align(Alignment.Start),
            fontWeight = FontWeight.Bold
        )
        Text(
            "Şagird kart-karta köçürməni edibsə, telefon nömrəsini bura yazıb açın:",
            modifier = Modifier.align(Alignment.Start),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    confirmation = null
                },
                label = { Text("Telefon nömrəsi") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val submitted = phone
                    // Təsdiq mesajı yalnız server uğurla cavab verəndən sonra göstərilir
                    onUnlock(submitted) { confirmation = submitted }
                },
                enabled = phone.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Aç") }
        }
        confirmation?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "\"$it\" nömrəsi açıldı.",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Admin panelinin altında görünən, cihaz sıfırlama bölməsi. Hər hesab ilk
 * aktivləşdiyi telefona bağlandığı üçün (paylaşılmanın qarşısını almaq üçün),
 * şagirdin telefonu dəyişəndə admin bura nömrəni yazıb sıfırlayır.
 */
@Composable
private fun DeviceResetSection(onReset: (String, () -> Unit) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<String?>(null) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Cihazı sıfırla",
            modifier = Modifier.align(Alignment.Start),
            fontWeight = FontWeight.Bold
        )
        Text(
            "Hər hesab ilk daxil olduğu telefona bağlanır (paylaşılmasın deyə). Şagirdin telefonu dəyişibsə, nömrəni bura yazıb sıfırlayın ki, yeni telefon həmin hesaba bağlana bilsin:",
            modifier = Modifier.align(Alignment.Start),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    confirmation = null
                },
                label = { Text("Telefon nömrəsi") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val submitted = phone
                    onReset(submitted) { confirmation = submitted }
                },
                enabled = phone.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Sıfırla") }
        }
        confirmation?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "\"$it\" nömrəsinin cihazı sıfırlandı.",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 13.sp
            )
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
    onAnswer: (Int) -> Unit,
    onExit: () -> Unit
) {
    // İmtahandan çıxış təsdiq dialoqunun görünüb-görünməməsini idarə edir
    var showExitConfirm by remember { mutableStateOf(false) }

    // Telefonun fiziki/jest "geri" hərəkəti də çıxış təsdiqini açsın - birbaşa
    // tətbiqdən çıxmasın (imtahan ortasında)
    BackHandler { showExitConfirm = true }

    // İstifadəçinin bu sualda seçdiyi variant. "remember(question.id)" sayəsində
    // hər yeni sualda avtomatik sıfırlanır (əks halda köhnə sualın seçimi qalardı).
    var selectedIndex by remember(question.id) { mutableStateOf<Int?>(null) }

    // Cavab seçiləndə telefon yüngülcə "titrəsin" ki, toxunuşun qəbul olunduğu
    // hiss olunsun (düzgün/səhv üçün fərqli hiss - ekrana baxmadan da sezilir)
    val haptic = LocalHapticFeedback.current

    // Seçim ediləndən qısa müddət sonra (səhv/düzgün göründükdən sonra) növbəti
    // suala keçirik - "Təsdiqlə" düyməsi olmadan, amma cavabı görmək üçün kifayət vaxt
    LaunchedEffect(selectedIndex, question.id) {
        val index = selectedIndex
        if (index != null) {
            delay(1200.milliseconds)
            onAnswer(index)
        }
    }

    // Vaxt 10 saniyədən az qalanda taймer qutusu yüngülcə "döyünür" (pulse) ki,
    // istifadəçi gözünü sualdan ayırmadan da vaxtın bitməkdə olduğunu hiss etsin
    val isCritical = timeLeft in 1..10
    val criticalPulse = rememberInfiniteTransition(label = "timerPulse")
    val pulseScale by criticalPulse.animateFloat(
        initialValue = 1f,
        targetValue = if (isCritical) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timerPulseScale"
    )

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("İmtahandan çıxmaq istəyirsiniz?") },
            text = { Text("Nəticəniz saxlanmayacaq və sınaqlar siyahısına qayıdacaqsınız.") },
            confirmButton = {
                Button(onClick = {
                    showExitConfirm = false
                    onExit()
                }) { Text("Çıx") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Davam et") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showExitConfirm = true }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "İmtahandan çıx",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column {
                        Text("Sual $currentNum", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Text("Ümumi: $totalNum", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Vaxt qutusu: dairəvi (pill) nişan, normalda isti "dərs" rənğində -
                    // 30 saniyədən az qalanda qırmızıya (xəbərdarlıq rənginə) dəyişir,
                    // 10 saniyədən az qalanda isə yuxarıdaki pulseScale sayəsində yüngülcə döyünür
                    Surface(
                        modifier = Modifier.scale(pulseScale),
                        color = if (timeLeft < 30) MaterialTheme.colorScheme.errorContainer else LessonOrangeContainer,
                        shape = RoundedCornerShape(percent = 50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = if (timeLeft < 30) MaterialTheme.colorScheme.error else LessonOrange
                            )
                            Spacer(Modifier.width(8.dp))
                            // Saniyəni "dəqiqə:saniyə" formatına çeviririk (məs. 125 saniyə -> "02:05")
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:%02d", timeLeft / 60, timeLeft % 60),
                                fontWeight = FontWeight.Black,
                                color = if (timeLeft < 30) MaterialTheme.colorScheme.error else LessonOrange
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Tərəqqi xətti əvəzinə ulduz-izi: 5 ulduzdan neçəsi dolu olduğu
                // neçənci sualda olduğumuzu göstərir (məs. 5/25 sualda 1 ulduz dolu) -
                // bu, sual sayından (25, 26...) asılı olmayaraq həmişə oxunaqlı qalır
                val filledStars = (currentNum.toFloat() / totalNum * 5).roundToInt().coerceIn(1, 5)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { i ->
                        Icon(
                            imageVector = if (i < filledStars) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (i < filledStars) LessonOrange else MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
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
            // Sualın özü (və varsa şəkli) bir kart içində göstərilir - sol kənarda
            // rəngli zolaq "dəftər vərəqi" hissi verir (məktəb dərsliyi üslubu)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.error)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(24.dp),
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
                                    .clip(RoundedCornerShape(16.dp)),
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
            }

            Spacer(Modifier.height(32.dp))

            // Cavab variantları: seçim ediləndə həmin variant səhvdirsə qırmızı,
            // düzgün variant isə (seçilməsə belə) yaşıl işıqlandırılır ki,
            // istifadəçi düzgün cavabı görsün - sonra qısa gecikmədən sonra
            // növbəti suala keçilir (bax: yuxarıdakı LaunchedEffect)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.options.forEachIndexed { index, option ->
                    val isAnswered = selectedIndex != null
                    val isCorrectOption = index == question.correctAnswerIndex
                    val isWrongSelection = isAnswered && index == selectedIndex && !isCorrectOption

                    val containerColor = when {
                        isAnswered && isCorrectOption -> MaterialTheme.colorScheme.secondaryContainer
                        isWrongSelection -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val contentColor = when {
                        isAnswered && isCorrectOption -> MaterialTheme.colorScheme.onSecondaryContainer
                        isWrongSelection -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val borderColor = when {
                        isAnswered && isCorrectOption -> MaterialTheme.colorScheme.secondary
                        isWrongSelection -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }

                    Surface(
                        onClick = {
                            if (selectedIndex == null) {
                                selectedIndex = index
                                // Düzgün/səhv cavab üçün fərqli haptic hiss - istifadəçi
                                // rənglərə baxmadan da nəticəni "əlində" hiss edir
                                haptic.performHapticFeedback(
                                    if (index == question.correctAnswerIndex) HapticFeedbackType.Confirm else HapticFeedbackType.Reject
                                )
                            }
                        },
                        enabled = selectedIndex == null,
                        modifier = Modifier.fillMaxWidth(),
                        // Tam dairəvi (pill) forma - məktəb dərsliyi üslubuna uyğun, əvvəlki
                        // künclü (12.dp) kartlardan daha "yumşaq/oyunlaşdırılmış" görünür
                        shape = RoundedCornerShape(percent = 50),
                        color = containerColor,
                        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = contentColor,
                                modifier = Modifier.weight(1f)
                            )
                            if (isAnswered && isCorrectOption) {
                                Icon(Icons.Default.Check, contentDescription = "Düzgün cavab", tint = contentColor)
                            } else if (isWrongSelection) {
                                Icon(Icons.Default.Close, contentDescription = "Səhv cavab", tint = contentColor)
                            }
                        }
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
 *
 * DİQQƏT: rənglər birbaşa (hardcoded) hex dəyərlər DEYİL, tema (MaterialTheme.colorScheme)
 * üzərindən götürülür - əks halda tünd rejimdə bu ekran işıqlı qalıb qalanlarla uyuşmazdı.
 */
@Composable
fun ResultScreen(userInfo: UserInfo?, score: Int, total: Int, onRestart: () -> Unit) {
    val isSuccess = score >= total / 2
    val accentColor = if (isSuccess) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    val accentContainer = if (isSuccess) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer

    // Faizə görə 0-3 ulduz (məktəb dərsliyi üslubunda performans göstəricisi) -
    // sadəcə keçdi/keçmədi deyil, nəticənin "nə qədər yaxşı" olduğunu da göstərir
    val percentage = if (total > 0) score.toFloat() / total else 0f
    val starCount = when {
        percentage >= 0.9f -> 3
        percentage >= 0.7f -> 2
        percentage >= 0.5f -> 1
        else -> 0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Uğurlu olanda yaşılımtıl, uğursuz olanda qırmızımtıl fon - temanın öz
                // "container" rənglərindən, ona görə tünd/açıq rejimdə avtomatik düzgün görünür
                Brush.verticalGradient(
                    colors = listOf(accentContainer, MaterialTheme.colorScheme.surface)
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
                imageVector = if (isSuccess) Icons.Default.EmojiEvents else Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = accentColor
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isSuccess) "Təbriklər!" else "Yenidən Cəhd Et!",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = accentColor
            )
            Text(
                text = "${userInfo?.firstName} ${userInfo?.lastName}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            // Performans ulduzları: nəticənin faizinə görə 0-3 dolu ulduz
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    Icon(
                        imageVector = if (i < starCount) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (i < starCount) LessonOrange else MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Xal kartı: böyük rəqəmlə "5 / 25" formatında nəticə
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
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
                shape = RoundedCornerShape(12.dp)
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
fun RoleSelectionPreview() {
    MyApplicationTheme {
        RoleSelectionScreen(onStudentClick = {}, onAdminVerify = { _, onResult -> onResult(false) }, onTeacherClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun PhoneEntryPreview() {
    MyApplicationTheme {
        PhoneEntryScreen(
            phoneNumber = "",
            isCheckingPhone = false,
            isReturningUser = false,
            isLoggingInWithPassword = false,
            onPhoneChange = {},
            onContinue = {},
            onPasswordLogin = {},
            onAdminVerify = { _, onResult -> onResult(false) },
            onAdminClick = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NameEntryPreview() {
    MyApplicationTheme {
        NameEntryScreen(isRegistering = false, onRegister = { _, _, _ -> }, onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
fun CategorySelectionPreview() {
    MyApplicationTheme {
        // "isUnlocked = false" - önizləmədə kilidlərin necə göründüyünü görmək üçün
        CategorySelectionScreen(categories = (1..25).map { Category(it) }, isUnlocked = false, onCategorySelected = {}, onLockedClick = {}, onAdminVerify = { _, onResult -> onResult(false) }, onAdminClick = {}, onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
fun AdminPreview() {
    MyApplicationTheme {
        AdminScreen(
            isLoading = false,
            categories = (1..25).map { Category(it) },
            categoryQuestions = emptyList(),
            onAddCategory = { _, _ -> },
            onCategoryChange = {},
            onDeleteCategory = {},
            onDeleteQuestion = { _, _ -> },
            onBack = {},
            onUpload = { _, _, _, _, _, _ -> },
            onManualUnlock = { _, _ -> },
            onDeviceReset = { _, _ -> },
            onBulkUpload = { _, _, _ -> }
        )
    }
}
