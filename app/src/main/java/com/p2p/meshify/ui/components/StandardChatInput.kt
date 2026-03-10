package com.p2p.meshify.ui.components

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Duo
import androidx.compose.material.icons.outlined.InsertPhoto
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.MutableState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.p2p.meshify.R
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 1:1 Carbon Copy of ChatApp-Compose UserInput component.
 * Three-Layer Architecture:
 * 1. UserInputText - Contains the Mic icon and BasicTextField
 * 2. UserInputSelector - Horizontal row of 5 icons + Send button
 * 3. SelectorExpanded - Expandable panel for Emojis and Recording feedback
 */
enum class InputSelector {
    NONE,
    MAP,
    DM,
    EMOJI,
    PHONE,
    PICTURE,
    MIC
}

@Preview
@Composable
fun StandardChatInputPreview() {
    StandardChatInput(onMessageChange = {})
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StandardChatInput(
    modifier: Modifier = Modifier,
    onMessageChange: (String) -> Unit
) {
    var currentInputSelector by rememberSaveable { mutableStateOf(InputSelector.NONE) }
    val dismissKeyboard = { currentInputSelector = InputSelector.NONE }

    // BackHandler to dismiss selectors
    if (currentInputSelector != InputSelector.NONE) {
        BackHandler(onBack = dismissKeyboard)
    }

    var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    // Track text field focus state to determine keyboard visibility
    var textFieldFocusState by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 2.dp,
        contentColor = MaterialTheme.colorScheme.secondary
    ) {
        Column(modifier = modifier) {
            UserInputText(
                onRecordTextChanged = { textState = textState.setText(it) },
                onSelectorChange = { currentInputSelector = it },
                textFieldValue = textState,
                onTextChanged = { textState = it },
                keyboardShown = currentInputSelector == InputSelector.NONE && textFieldFocusState,
                onTextFieldFocused = { focused ->
                    if (focused) {
                        currentInputSelector = InputSelector.NONE
                    }
                    textFieldFocusState = focused
                }
            )
            UserInputSelector(
                onSelectorChange = { currentInputSelector = it },
                sendMessageEnabled = textState.text.isNotBlank(),
                onMessageSent = {
                    onMessageChange(textState.text)
                    textState = TextFieldValue()
                    dismissKeyboard()
                },
                currentInputSelector = currentInputSelector
            )
            SelectorExpanded(
                onTextAdded = { textState = textState.addText(it) },
                currentSelector = currentInputSelector
            )
        }
    }
}

// Extension: Add text after existing text
private fun TextFieldValue.addText(newString: String): TextFieldValue {
    val newText = this.text.replaceRange(
        this.selection.start,
        this.selection.end,
        newString
    )
    val newSelection = TextRange(
        start = newText.length,
        end = newText.length
    )
    return this.copy(text = newText, selection = newSelection)
}

// Extension: Replace text with new text
private fun TextFieldValue.setText(newString: String): TextFieldValue {
    val newSelection = TextRange(
        start = newString.length,
        end = newString.length
    )
    return this.copy(text = newString, selection = newSelection)
}

/**
 * Layer 3: SelectorExpanded - Expandable panel for Emojis and Recording
 */
@Composable
private fun SelectorExpanded(
    currentSelector: InputSelector,
    onTextAdded: (String) -> Unit
) {
    if (currentSelector == InputSelector.NONE) return

    // Request focus to force TextField to lose focus
    val focusRequester = FocusRequester()
    SideEffect {
        if (currentSelector == InputSelector.EMOJI) {
            focusRequester.requestFocus()
        }
    }

    Surface(tonalElevation = 8.dp) {
        when (currentSelector) {
            InputSelector.EMOJI -> EmojiSelector(onTextAdded, focusRequester)
            InputSelector.DM -> FunctionalityNotAvailablePanel()
            InputSelector.PICTURE -> FunctionalityNotAvailablePanel()
            InputSelector.MAP -> FunctionalityNotAvailablePanel()
            InputSelector.PHONE -> FunctionalityNotAvailablePanel()
            InputSelector.MIC -> RecordPanel()
            else -> {
                throw NotImplementedError()
            }
        }
    }
}

/**
 * Layer 2: UserInputSelector - Horizontal row of 5 icons + Send button
 * Height: 72.dp exactly as in original
 */
@Composable
private fun UserInputSelector(
    onSelectorChange: (InputSelector) -> Unit,
    sendMessageEnabled: Boolean,
    onMessageSent: () -> Unit,
    currentInputSelector: InputSelector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(72.dp)
            .wrapContentHeight()
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InputSelectorButton(
            onClick = { onSelectorChange(InputSelector.EMOJI) },
            icon = Icons.Outlined.Mood,
            selected = currentInputSelector == InputSelector.EMOJI,
            description = stringResource(id = R.string.emoji_selector_bt_desc)
        )
        InputSelectorButton(
            onClick = { onSelectorChange(InputSelector.DM) },
            icon = Icons.Outlined.AlternateEmail,
            selected = currentInputSelector == InputSelector.DM,
            description = stringResource(id = R.string.dm_desc)
        )
        InputSelectorButton(
            onClick = { onSelectorChange(InputSelector.PICTURE) },
            icon = Icons.Outlined.InsertPhoto,
            selected = currentInputSelector == InputSelector.PICTURE,
            description = stringResource(id = R.string.attach_photo_desc)
        )
        InputSelectorButton(
            onClick = { onSelectorChange(InputSelector.MAP) },
            icon = Icons.Outlined.Place,
            selected = currentInputSelector == InputSelector.MAP,
            description = stringResource(id = R.string.map_selector_desc)
        )
        InputSelectorButton(
            onClick = { onSelectorChange(InputSelector.PHONE) },
            icon = Icons.Outlined.Duo,
            selected = currentInputSelector == InputSelector.PHONE,
            description = stringResource(id = R.string.videochat_desc)
        )

        val border = if (!sendMessageEnabled) {
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        } else {
            null
        }
        Spacer(modifier = Modifier.weight(1f))

        val disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

        val buttonColors = ButtonDefaults.buttonColors(
            disabledContainerColor = Color.Transparent,
            disabledContentColor = disabledContentColor
        )

        // Send button - M3 Button with icon and text
        Button(
            modifier = Modifier.height(36.dp),
            enabled = sendMessageEnabled,
            onClick = onMessageSent,
            colors = buttonColors,
            border = border,
        ) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = stringResource(id = R.string.send_desc)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(id = R.string.send))
        }
    }
}

/**
 * Input selector button with selection state
 */
@Composable
private fun InputSelectorButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundModifier = if (selected) {
        Modifier.background(
            color = LocalContentColor.current,
            shape = RoundedCornerShape(14.dp)
        )
    } else {
        Modifier
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.then(backgroundModifier)
    ) {
        val tint = if (selected) {
            contentColorFor(backgroundColor = LocalContentColor.current)
        } else {
            LocalContentColor.current
        }
        Icon(
            icon,
            tint = tint,
            modifier = Modifier
                .padding(8.dp)
                .size(56.dp),
            contentDescription = description
        )
    }
}

/**
 * Semantics key for keyboard shown state
 */
val KeyboardShownKey = SemanticsPropertyKey<Boolean>("KeyboardShownKey")
var SemanticsPropertyReceiver.keyboardShownProperty by KeyboardShownKey

/**
 * Layer 1: UserInputText - Contains the Mic icon and BasicTextField
 * Font size: 22.sp exactly as in original
 */
@OptIn(ExperimentalPermissionsApi::class)
@ExperimentalFoundationApi
@Composable
private fun UserInputText(
    onSelectorChange: (InputSelector) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    onTextChanged: (TextFieldValue) -> Unit,
    textFieldValue: TextFieldValue,
    keyboardShown: Boolean,
    onTextFieldFocused: (Boolean) -> Unit,
    onRecordTextChanged: (String) -> Unit
) {
    var isRecordingMessage by remember { mutableStateOf(false) }
    val label = stringResource(id = R.string.textfield_desc)
    val noInternet = stringResource(id = R.string.noInternet)
    val noPermissions = stringResource(id = R.string.noPermission)
    val multiplePermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.RECORD_AUDIO
        )
    )
    val context = LocalContext.current
    val myRecognitionListener = MyRecognitionListener()
    val mySpeechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    mySpeechRecognizer.setRecognitionListener(myRecognitionListener)
    val result = remember {
        mutableStateOf("")
    }
    myRecognitionListener.setResult(result)
    DisposableEffect(result.value) {
        onRecordTextChanged(result.value)

        onDispose {
            // Clean up any resources if needed
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.End
    ) {

        IconButton(
            onClick = {
                if (!isRecordingMessage) {
                    multiplePermissionState.launchMultiplePermissionRequest()
                    when {
                        multiplePermissionState.allPermissionsGranted -> {
                            if (isNetworkAvailable(context)) {
                                val recognitionIntent =
                                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                recognitionIntent.putExtra(
                                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                                    true
                                )
                                mySpeechRecognizer.startListening(recognitionIntent)
                                onSelectorChange(InputSelector.MIC)
                            } else {
                                Toast.makeText(
                                    context,
                                    noInternet,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        multiplePermissionState.shouldShowRationale -> {
                            Toast.makeText(
                                context,
                                noPermissions,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                } else {
                    onSelectorChange(InputSelector.NONE)
                    mySpeechRecognizer.stopListening()
                }
                isRecordingMessage = !isRecordingMessage
            },
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentSize(Alignment.Center)
                .padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = stringResource(id = R.string.record)
            )
        }


        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 0.dp, top = 8.dp, end = 8.dp, bottom = 0.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ),
        ) {

            UserInputTextField(
                textFieldValue,
                onTextChanged,
                onTextFieldFocused,
                keyboardType,
                Modifier.semantics {
                    contentDescription = label
                    keyboardShownProperty = keyboardShown
                }
            )

        }
    }
}

/**
 * BasicTextField with RoundedCornerShape(8.dp) and fontSize = 22.sp
 * Exact copy from ChatApp-Compose
 */
@Composable
private fun BoxScope.UserInputTextField(
    textFieldValue: TextFieldValue,
    onTextChanged: (TextFieldValue) -> Unit,
    onTextFieldFocused: (Boolean) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    var lastFocusState by remember { mutableStateOf(false) }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { onTextChanged(it) },
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .align(Alignment.Center)
            .onFocusChanged { state ->
                if (lastFocusState != state.isFocused) {
                    onTextFieldFocused(state.isFocused)
                }
                lastFocusState = state.isFocused
            }
            .background(MaterialTheme.colorScheme.surface),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done
        ),
        textStyle = LocalTextStyle.current.copy(
            color = LocalContentColor.current,
            fontSize = 22.sp
        )
    )
}

/**
 * Recording Indicator with pulsing red dot and timer
 * 1:1 Copy from RecordButton.kt
 */
@Composable
internal fun RecordingIndicator() {
    var duration by remember { mutableStateOf(Duration.ZERO) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            duration += 1.seconds
        }
    }
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")

        val animatedPulse = infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                tween(2000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse",
        )
        Box(
            Modifier
                .size(30.dp)
                .padding(8.dp)
                .graphicsLayer {
                    scaleX = animatedPulse.value
                    scaleY = animatedPulse.value
                }
                .clip(CircleShape)
                .background(Color.Red)
        )
        Text(
            duration.toComponents { minutes, seconds, _ ->
                val min = minutes.toString().padStart(2, '0')
                val sec = seconds.toString().padStart(2, '0')
                "$min:$sec"
            },
            Modifier.align(Alignment.CenterVertically)
        )
    }
}

/**
 * Record Panel with RecordingIndicator
 * 1:1 Copy from Panels.kt
 */
@Composable
fun RecordPanel() {
    AnimatedVisibility(
        visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .height(60.dp)
                .fillMaxWidth()
                .wrapContentSize(Alignment.Center)
        ) {
            RecordingIndicator()
        }
    }
}

/**
 * Functionality Not Available Panel
 * 1:1 Copy from Panels.kt
 */
@Composable
fun FunctionalityNotAvailablePanel() {
    AnimatedVisibility(
        visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(
            modifier = Modifier
                .height(320.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(id = R.string.not_available),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(id = R.string.not_available_subtitle),
                modifier = Modifier.paddingFrom(FirstBaseline, before = 32.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Emoji Selector with FocusRequester
 * 1:1 Copy from Panels.kt
 */
@Composable
fun EmojiSelector(
    onTextAdded: (String) -> Unit,
    focusRequester: FocusRequester
) {

    val a11yLabel = stringResource(id = R.string.emoji_selector_bt_desc)
    Column(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusTarget()
            .semantics { contentDescription = a11yLabel }
    ) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            EmojiTable(onTextAdded, modifier = Modifier.padding(8.dp))
        }
    }
}

/**
 * Emoji Table with 4 rows x 10 columns = 40 emojis
 * 1:1 Copy from Panels.kt
 */
@Composable
fun EmojiTable(
    onTextAdded: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.wrapContentSize()) {
        repeat(4) { x ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(EMOJI_COLUMNS) { y ->
                    val emoji = emojis[x * EMOJI_COLUMNS + y]
                    Text(
                        modifier = Modifier
                            .clickable(onClick = { onTextAdded(emoji) })
                            .sizeIn(minWidth = 42.dp, minHeight = 42.dp)
                            .padding(8.dp),
                        text = emoji,
                        style = LocalTextStyle.current.copy(
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}

/**
 * MyRecognitionListener for speech recognition
 * 1:1 Copy from MyRecognitionListener.kt
 */
class MyRecognitionListener : RecognitionListener {

    private lateinit var result: MutableState<String>
    fun setResult(value: MutableState<String>) {
        result = value
    }

    override fun onReadyForSpeech(params: Bundle?) {}

    override fun onBeginningOfSpeech() {
        result.value = ""
    }

    override fun onRmsChanged(rmsdB: Float) {
    }

    override fun onBufferReceived(buffer: ByteArray?) {
    }

    override fun onEndOfSpeech() {
    }

    override fun onError(error: Int) {
        android.util.Log.e("Recognition", error.toString())
    }

    override fun onResults(results: Bundle?) {
        val resultArray =
            results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
        result.value = resultArray?.get(0) ?: return
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val resultArray =
            partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
        result.value = resultArray?.get(0) ?: return
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
    }
}

/**
 * Network utility function
 * 1:1 Copy from Utils.kt
 */
fun isNetworkAvailable(context: android.content.Context): Boolean {
    val connectivityManager = context
        .getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val networkCapabilities = connectivityManager
        .getNetworkCapabilities(connectivityManager.activeNetwork)
    return networkCapabilities != null &&
            networkCapabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * Emoji data array - 40 emojis (4 rows x 10 columns)
 */
private const val EMOJI_COLUMNS = 10
private val emojis = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
    "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "☺️", "😚",
    "😙", "🥲", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭",
    "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄"
)
