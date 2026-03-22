package com.p2p.meshify.feature.onboarding

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.hooks.HapticPattern
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.core.ui.theme.MeshifyDesignSystem
import kotlinx.coroutines.launch

/**
 * Main onboarding screen composable.
 * Implements swipe support, page indicator, and navigation buttons.
 */
@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    onGetStartedClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPrivacyPolicyClick: () -> Unit = {},
    onTermsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val pages = viewModel.getAllPages()
    val scope = rememberCoroutineScope()
    val haptics = LocalPremiumHaptics.current
    
    // Double-tap protection
    var lastClickTime by remember { mutableStateOf(0L) }
    val debounceDuration = 500L
    
    // Pager state for swipe support
    val pagerState = rememberPagerState(
        initialPage = 0,
        initialPageOffsetFraction = 0f
    ) {
        uiState.totalPages
    }
    
    // Sync pager state with ViewModel state
    LaunchedEffect(uiState.currentPage) {
        if (pagerState.currentPage != uiState.currentPage) {
            pagerState.animateScrollToPage(
                page = uiState.currentPage,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 350f
                )
            )
        }
    }
    
    // Sync ViewModel when user swipes
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != uiState.currentPage && !uiState.isAnimating) {
            viewModel.goToPage(pagerState.currentPage)
        }
    }
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Section — Skip button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MeshifyDesignSystem.Spacing.Md),
                contentAlignment = Alignment.TopEnd
            ) {
                if (!uiState.isLastPage) {
                    TextButton(
                        onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime >= debounceDuration) {
                                lastClickTime = currentTime
                                haptics.perform(HapticPattern.Tick)
                                viewModel.skipOnboarding()
                            }
                        },
                        enabled = !uiState.isAnimating
                    ) {
                        Text(
                            text = stringResource(id = R.string.onboarding_btn_skip),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Main Content — HorizontalPager with swipe support
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
                pageSpacing = MeshifyDesignSystem.Spacing.Xl
            ) { page ->
                val pageInfo = pages[page]
                
                AnimatedVisibility(
                    visible = true,
                    enter = slideInHorizontally(
                        initialOffsetX = { if (it > 0) it else -it },
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = 350f
                        )
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = slideOutHorizontally(
                        targetOffsetX = { if (it > 0) -it else it },
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = 350f
                        )
                    ) + fadeOut(animationSpec = tween(300))
                ) {
                    OnboardingPage(
                        pageInfo = pageInfo,
                        modifier = Modifier.fillMaxSize(),
                        onPrivacyPolicyClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime >= debounceDuration) {
                                lastClickTime = currentTime
                                haptics.perform(HapticPattern.Tick)
                                onPrivacyPolicyClick()
                            }
                        },
                        onTermsClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime >= debounceDuration) {
                                lastClickTime = currentTime
                                haptics.perform(HapticPattern.Tick)
                                onTermsClick()
                            }
                        }
                    )
                }
            }
            
            // Bottom Section — Page indicator and Next/Get Started button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MeshifyDesignSystem.Spacing.Lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Lg)
            ) {
                // Page Indicator Dots
                PageIndicator(
                    totalPages = uiState.totalPages,
                    currentPage = uiState.currentPage,
                    onPageSelected = { pageIndex ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime >= debounceDuration) {
                            lastClickTime = currentTime
                            haptics.perform(HapticPattern.Tick)
                            viewModel.goToPage(pageIndex)
                        }
                    },
                    isAnimating = uiState.isAnimating
                )
                
                // Next / Get Started Button
                NavigationButton(
                    isLastPage = uiState.isLastPage,
                    isAnimating = uiState.isAnimating,
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime >= debounceDuration) {
                            lastClickTime = currentTime
                            haptics.perform(HapticPattern.Pop)
                            
                            if (uiState.isLastPage) {
                                onGetStartedClick()
                            } else {
                                viewModel.nextPage()
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Page indicator with animated dots.
 */
@Composable
private fun PageIndicator(
    totalPages: Int,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    isAnimating: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MeshifyDesignSystem.Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalPages) {
            PageDot(
                isActive = i == currentPage,
                isAnimating = isAnimating,
                onClick = { onPageSelected(i) },
                pageIndex = i,
                currentPage = currentPage
            )
        }
    }
}

/**
 * Individual page indicator dot.
 */
@SuppressLint("MissingPermission")
@Composable
private fun PageDot(
    isActive: Boolean,
    isAnimating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pageIndex: Int = 0,
    currentPage: Int = 0
) {
    val context = LocalContext.current
    val haptics = LocalPremiumHaptics.current
    
    // Get vibrator for haptic feedback
    val vibrator = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(VibratorManager::class.java)
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }
    
    val dotModifier = if (isActive) {
        modifier
            .size(12.dp)
            .semantics { contentDescription = "Page ${currentPage + 1}" }
    } else {
        modifier
            .size(8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptics.perform(HapticPattern.Tick)

                    // Light haptic for dot tap
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            vibrator?.vibrate(
                                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                            )
                        }
                    } catch (e: SecurityException) {
                        // VIBRATE permission not granted — ignore
                    }
                    onClick()
                }
            )
            .semantics { contentDescription = "Go to page ${pageIndex + 1}" }
    }
    
    Box(
        modifier = dotModifier
            .background(
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
                shape = CircleShape
            )
    )
}

/**
 * Navigation button (Next / Get Started).
 */
@Composable
private fun NavigationButton(
    isLastPage: Boolean,
    isAnimating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var buttonScale by remember { mutableStateOf(1f) }
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            // Button press animation
            buttonScale = 0.92f

            // Trigger click after animation
            scope.launch {
                kotlinx.coroutines.delay(50)
                buttonScale = 1f
            }

            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(buttonScale),
        shape = MeshifyDesignSystem.Shapes.Button,
        enabled = !isAnimating
    ) {
        Text(
            text = if (isLastPage) {
                stringResource(id = R.string.onboarding_btn_get_started)
            } else {
                stringResource(id = R.string.onboarding_btn_next)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
