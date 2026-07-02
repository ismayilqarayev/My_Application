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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                TestApp()
            }
        }
    }
}

@Composable
fun TestApp(viewModel: TestViewModel = viewModel()) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = viewModel.screenState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ScreenTransition"
        ) { state ->
            when (state) {
                ScreenState.Registration -> RegistrationScreen(
                    onRegister = viewModel::register,
                    onAdminClick = { viewModel.screenState = ScreenState.Admin }
                )
                ScreenState.CategorySelection -> CategorySelectionScreen(
                    onCategorySelected = viewModel::selectCategory
                )
                ScreenState.Testing -> {
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
    }
}

@Composable
fun RegistrationScreen(onRegister: (String, String) -> Unit, onAdminClick: () -> Unit) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    
    // Admin girişi üçün state-lər
    var showAdminLogin by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

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
                            isError = false
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
                        if (password == "admin777") { // Admin parolu
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
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { onRegister(firstName, lastName) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = firstName.isNotBlank() && lastName.isNotBlank()
                    ) {
                        Text("İmtahana Başla", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectionScreen(onCategorySelected: (String) -> Unit) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            categories.chunked(3).forEach { rowCategories ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowCategories.forEach { (name, _) ->
                        val number = name.filter { it.isDigit() }.toIntOrNull() ?: 0
                        val isLocked = number >= 6
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Surface(
                                    onClick = { if (!isLocked) onCategorySelected(name) },
                                    modifier = Modifier.size(90.dp),
                                    shape = RoundedCornerShape(24.dp),
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
                                
                                if (isLocked) {
                                    Surface(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .offset(x = 6.dp, y = (-6).dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    isLoading: Boolean,
    onBack: () -> Unit,
    onUpload: (String, String, List<String>, Int, Uri?) -> Unit
) {
    var category by remember { mutableStateOf("sınaq 1") }
    var text by remember { mutableStateOf("") }
    var option1 by remember { mutableStateOf("") }
    var option2 by remember { mutableStateOf("") }
    var option3 by remember { mutableStateOf("") }
    var option4 by remember { mutableStateOf("") }
    var correctIndex by remember { mutableIntStateOf(0) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    
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

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Sualın mətni") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text("Variantlar (Düzgün olanı seçin):", modifier = Modifier.align(Alignment.Start), fontWeight = FontWeight.Bold)
            
            val options = listOf(option1, option2, option3, option4)
            val setters = listOf<(String) -> Unit>({ option1 = it }, { option2 = it }, { option3 = it }, { option4 = it })
            
            options.forEachIndexed { index, opt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                enabled = !isLoading && text.isNotBlank() && option1.isNotBlank() && option2.isNotBlank()
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) 
                else Text("Bazada Yadda Saxla", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

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
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:%02d", timeLeft / 60, timeLeft % 60),
                                fontWeight = FontWeight.Black,
                                color = if (timeLeft < 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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

@Composable
fun ResultScreen(userInfo: UserInfo?, score: Int, total: Int, onRestart: () -> Unit) {
    val isSuccess = score >= total / 2
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
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
        CategorySelectionScreen(onCategorySelected = {})
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
