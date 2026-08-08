package com.example.myapplication

import android.app.Activity
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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.myapplication.BuildConfig
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
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

    // Firebase Phone Auth-ın "reCAPTCHA" ehtiyatı üçün Activity lazımdır
    val activity = LocalContext.current as Activity

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
        delay(TRANSITION_DURATION_MS)
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
                    onTeacherClick = { viewModel.screenState = ScreenState.Admin }
                )
                ScreenState.PhoneEntry -> PhoneEntryScreen(
                    phoneNumber = viewModel.phoneNumber,
                    isSendingCode = viewModel.isSendingCode,
                    isCheckingPhone = viewModel.isCheckingPhone,
                    isReturningUser = viewModel.isReturningUser,
                    isLoggingInWithPassword = viewModel.isLoggingInWithPassword,
                    onPhoneChange = viewModel::updatePhoneNumber,
                    onContinue = { viewModel.checkPhoneAndProceed(activity) },
                    onPasswordLogin = viewModel::loginWithPassword,
                    onAdminClick = { viewModel.screenState = ScreenState.Admin },
                    onBack = { viewModel.screenState = ScreenState.RoleSelection }
                )
                ScreenState.CodeEntry -> CodeEntryScreen(
                    isVerifyingCode = viewModel.isVerifyingCode,
                    onVerifyCode = viewModel::verifyCode,
                    onBack = { viewModel.screenState = ScreenState.PhoneEntry }
                )
                ScreenState.NameEntry -> NameEntryScreen(
                    onRegister = viewModel::register,
                    onBack = { viewModel.screenState = ScreenState.RoleSelection }
                )
                ScreenState.CategorySelection -> CategorySelectionScreen(
                    isUnlocked = viewModel.isUnlocked,
                    onCategorySelected = viewModel::selectCategory,
                    onLockedClick = { showPaymentDialog = true },   // kilidli sınağa klik -> ödəniş dialoqu aç
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
                    onBack = {
                        // Admin panelinə hardan gəlinibsə (giriş edilməzdən əvvəl və ya sonra),
                        // müvafiq ekrana qayıtsın
                        viewModel.screenState =
                            if (viewModel.userInfo != null) ScreenState.CategorySelection
                            else ScreenState.RoleSelection
                    },
                    onUpload = viewModel::uploadQuestion,
                    onManualUnlock = viewModel::manualUnlock,
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
 */
@Composable
fun AdminLoginDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }   // yanlış parol yazılıbsa true olur

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
                        onSuccess()
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
            TextButton(onClick = onDismiss) {
                Text("Ləğv Et")
            }
        }
    )
}

/**
 * Tətbiqin ƏSL İLK ekranı: istifadəçi "Şagird" və ya "Müəllim" olduğunu seçir.
 *  - "Şagird" -> telefon+SMS girişinə keçir (PhoneEntryScreen)
 *  - "Müəllim" -> admin parolunu soruşur, düzgündürsə birbaşa Admin panelinə keçir
 */
@Composable
fun RoleSelectionScreen(onStudentClick: () -> Unit, onTeacherClick: () -> Unit) {
    var showAdminLogin by remember { mutableStateOf(false) }

    if (showAdminLogin) {
        AdminLoginDialog(
            onDismiss = { showAdminLogin = false },
            onSuccess = {
                showAdminLogin = false
                onTeacherClick()
            }
        )
    }

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
    isSendingCode: Boolean,
    isCheckingPhone: Boolean,
    isReturningUser: Boolean,
    isLoggingInWithPassword: Boolean,
    onPhoneChange: (String) -> Unit,
    onContinue: () -> Unit,
    onPasswordLogin: (String) -> Unit,
    onAdminClick: () -> Unit,
    onBack: () -> Unit
) {
    var showAdminLogin by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val isBusy = isSendingCode || isCheckingPhone || isLoggingInWithPassword

    // Telefonun fiziki/jest "geri" hərəkəti də rol seçiminə qaytarsın (tətbiqdən
    // birbaşa çıxmaq əvəzinə)
    BackHandler(onBack = onBack)

    if (showAdminLogin) {
        AdminLoginDialog(
            onDismiss = { showAdminLogin = false },
            onSuccess = {
                showAdminLogin = false
                onAdminClick()
            }
        )
    }

    // Ekranın əsas gövdəsi: gradient fon + ortada giriş kartı
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
        // Rol seçiminə qayıtma düyməsi
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
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
 * SMS ilə göndərilən 6 rəqəmli təsdiq kodunu daxil etmə ekranı.
 */
@Composable
fun CodeEntryScreen(
    isVerifyingCode: Boolean,
    onVerifyCode: (String) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    // Telefonun fiziki/jest "geri" hərəkəti də telefon nömrəsi ekranına qaytarsın
    BackHandler(onBack = onBack)

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
        // Telefon nömrəsi ekranına qayıtma düyməsi (digər ekranlarla eyni yerdə/görünüşdə)
        IconButton(
            onClick = onBack,
            enabled = !isVerifyingCode,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
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
                        text = "Təsdiq Kodu",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "SMS ilə göndərilən 6 rəqəmli kodu daxil edin",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Kod") },
                        singleLine = true,
                        enabled = !isVerifyingCode,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { onVerifyCode(code) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isVerifyingCode && code.length >= 6
                    ) {
                        if (isVerifyingCode) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Text("Təsdiqlə", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onBack, enabled = !isVerifyingCode) {
                        Text("Nömrəni dəyişmək istəyirəm")
                    }
                }
            }
        }
    }
}

/**
 * Telefon təsdiqləndikdən sonra, YALNIZ YENİ hesablar üçün göstərilən
 * ad-soyad daxil etmə ekranı. Qayıdan istifadəçilər bu ekranı görmür,
 * çünki ad-soyadları "users/<telefon>" düyünündən avtomatik bərpa olunur.
 */
@Composable
fun NameEntryScreen(onRegister: (String, String, String) -> Unit, onBack: () -> Unit) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Telefonun fiziki/jest "geri" hərəkəti də rol seçiminə qaytarsın
    BackHandler(onBack = onBack)

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
        // Rol seçiminə qayıtma düyməsi (digər ekranlarla eyni yerdə/görünüşdə)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Soyad") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Parol təyin edin") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        text = "Növbəti girişlərdə SMS əvəzinə bu parol istifadə olunacaq (ən azı 4 simvol)",
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
                        enabled = firstName.isNotBlank() && lastName.isNotBlank() && password.length >= 4
                    ) {
                        Text("İmtahana Başla", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
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
    onLockedClick: () -> Unit,
    onAdminClick: () -> Unit,
    onBack: () -> Unit
) {
    // 25 ədəd "sınaq N" adlı kateqoriya siyahısı yaradılır (statik, hər dəfə eynidir)
    val categories = (1..25).map { "sınaq $it" to "📝" }
    var showAdminLogin by remember { mutableStateOf(false) }

    // Telefonun fiziki/jest "geri" hərəkəti də rol seçiminə qaytarsın
    BackHandler(onBack = onBack)

    if (showAdminLogin) {
        AdminLoginDialog(
            onDismiss = { showAdminLogin = false },
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                // Rol seçiminə qayıtma (digər ekranlarla eyni görünüşdə)
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
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
                                    shape = RoundedCornerShape(16.dp),
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

// Kart-karta köçürmə üçün göstərilən məlumatlar. Dəyişmək istəsəniz elə bu iki sətri redaktə edin.
private const val PAYMENT_CARD_NUMBER = "5239 1524 3334 0354"
private const val PAYMENT_AMOUNT_AZN = "5"

/**
 * 4-cü sınaqdan yuxarı bir kateqoriyaya klikləyəndə açılan pəncərə.
 *
 * AXIN (bank inteqrasiyası OLMADAN, sadə kart-karta köçürmə ilə):
 *  1) İstifadəçiyə kart nömrəsi və məbləğ göstərilir (telefon nömrəsi artıq
 *     giriş zamanı təsdiqləndiyi üçün yenidən soruşulmur)
 *  2) İstifadəçi köçürməni öz bank tətbiqi ilə edir (bizim tətbiqin xaricində)
 *  3) Admin (Admin panelindən) köçürməni gördükdən sonra əl ilə həmin nömrəni açır
 *  4) Bu pəncərə açıq qaldığı müddətdə status CANLI dinlənilir (giriş zamanı
 *     artıq başladılıb) - açılan kimi avtomatik "Açıldı!" mesajına keçir
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
                        // DİQQƏT: "phoneNumber" girişdən sonra artıq ölkə kodunu (994) ÖZÜNDƏ
                        // saxlayır (bax: TestViewModel.loadUserOrAskName), ona görə burada
                        // yenidən "994" yazmırıq - yalnız "+" əlavə edirik ki, ikiqat görünməsin
                        text = "Köçürmə edərkən hesabınıza bağlı telefon nömrəsini (+${viewModel.phoneNumber}) admininə bildirin.",
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
    onBack: () -> Unit,
    onUpload: (String, String, List<String>, Int, Uri?) -> Unit,
    onManualUnlock: (String) -> Unit,
    onBulkUpload: (String, String) -> Unit
) {
    var category by remember { mutableStateOf("sınaq 1") }   // hansı sınağa sual əlavə olunur
    var text by remember { mutableStateOf("") }                // sualın mətni
    var option1 by remember { mutableStateOf("") }
    var option2 by remember { mutableStateOf("") }
    var option3 by remember { mutableStateOf("") }
    var option4 by remember { mutableStateOf("") }
    var correctIndex by remember { mutableIntStateOf(0) }       // hansı variant (0-3) düzgündür
    var imageUri by remember { mutableStateOf<Uri?>(null) }     // seçilmiş şəklin telefon daxilindəki ünvanı

    // Telefonun fiziki/jest "geri" hərəkəti də əvvəlki ekrana qaytarsın
    BackHandler(onBack = onBack)

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
                    .clip(RoundedCornerShape(16.dp))
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
                shape = RoundedCornerShape(12.dp)
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
                shape = RoundedCornerShape(12.dp),
                // Sual mətni və ən azı 2 variant doldurulmayınca, yüklənərkən isə düymə deaktivdir
                enabled = !isLoading && text.isNotBlank() && option1.isNotBlank() && option2.isNotBlank()
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Bazada Yadda Saxla", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(48.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Bir dəfəyə çoxlu sual əlavə etmək bölməsi: yuxarıda seçilmiş
            // kateqoriyaya (chip-lərdən) tətbiq olunur.
            BulkUploadSection(category = category, onBulkUpload = onBulkUpload)

            Spacer(Modifier.height(48.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Ödənişi əl ilə açma bölməsi: şagird kart-karta köçürməni etdikdən
            // sonra, admin bura həmin telefon nömrəsini yazıb "Aç" düyməsinə basır -
            // bank inteqrasiyası olmadan, birbaşa Firebase-ə yazılır (pulsuzdur).
            ManualUnlockSection(onUnlock = onManualUnlock)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Admin panelinin ortasında görünən, bir dəfəyə çoxlu sual əlavə etmə bölməsi.
 * Format: hər sual boş sətirlə ayrılır, sualın altındakı sətirlər variantlardır,
 * düzgün variantın əvvəlinə "*" qoyulur.
 */
@Composable
private fun BulkUploadSection(category: String, onBulkUpload: (String, String) -> Unit) {
    var bulkText by remember { mutableStateOf("") }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Toplu sual əlavə et",
            modifier = Modifier.align(Alignment.Start),
            fontWeight = FontWeight.Bold
        )
        Text(
            "Hər sualı boş sətirlə ayırın. Sualın altındakı sətirlər variantlardır, düzgün olanın əvvəlinə \"*\" qoyun. Yuxarıda seçili kateqoriyaya (\"$category\") əlavə olunacaq.",
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
            onClick = { onBulkUpload(category, bulkText) },
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
private fun ManualUnlockSection(onUnlock: (String) -> Unit) {
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
                    onUnlock(phone)
                    confirmation = phone
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
                    // Vaxt qutusu: 30 saniyədən az qalanda rəngi qırmızıya (xəbərdarlıq rənginə) dəyişir
                    Surface(
                        color = if (timeLeft < 30) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
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
                shape = RoundedCornerShape(20.dp),
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

            Spacer(Modifier.height(32.dp))

            // Cavab variantları: hər birinə klikləmək dərhal "onAnswer(index)" çağırır -
            // yəni "Təsdiqlə" düyməsi yoxdur, seçim edən kimi növbəti suala keçir
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.options.forEachIndexed { index, option ->
                    Surface(
                        onClick = { onAnswer(index) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
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
        RoleSelectionScreen(onStudentClick = {}, onTeacherClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun PhoneEntryPreview() {
    MyApplicationTheme {
        PhoneEntryScreen(
            phoneNumber = "",
            isSendingCode = false,
            isCheckingPhone = false,
            isReturningUser = false,
            isLoggingInWithPassword = false,
            onPhoneChange = {},
            onContinue = {},
            onPasswordLogin = {},
            onAdminClick = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CodeEntryPreview() {
    MyApplicationTheme {
        CodeEntryScreen(isVerifyingCode = false, onVerifyCode = {}, onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
fun NameEntryPreview() {
    MyApplicationTheme {
        NameEntryScreen(onRegister = { _, _, _ -> }, onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
fun CategorySelectionPreview() {
    MyApplicationTheme {
        // "isUnlocked = false" - önizləmədə kilidlərin necə göründüyünü görmək üçün
        CategorySelectionScreen(isUnlocked = false, onCategorySelected = {}, onLockedClick = {}, onAdminClick = {}, onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
fun AdminPreview() {
    MyApplicationTheme {
        AdminScreen(
            isLoading = false,
            onBack = {},
            onUpload = { _, _, _, _, _ -> },
            onManualUnlock = {},
            onBulkUpload = { _, _ -> }
        )
    }
}
