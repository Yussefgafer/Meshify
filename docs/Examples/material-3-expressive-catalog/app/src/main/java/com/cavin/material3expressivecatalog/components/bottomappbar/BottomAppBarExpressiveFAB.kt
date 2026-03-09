package com.cavin.material3expressivecatalog.components.bottomappbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

@Composable
fun BottomAppBarExpressiveFAB(
    onNavigateBack: () -> Unit, // Parameter for back navigation
    modifier: Modifier = Modifier
) {
    // 1. State for FAB bounce animation
    var fabClicked by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (fabClicked) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "fab_scale_animation"
    )

    // 2. Scroll State and Dynamic Visibility Logic
    val scrollState = rememberLazyListState()
    val isScrollingUp = scrollState.isScrollingUp()

    val bottomBarVisible = remember(scrollState.isScrollInProgress, isScrollingUp) {
        !scrollState.isScrollInProgress || isScrollingUp
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Expressive Docked FAB") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { // 👈 Back Button Implementation
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }
            )
        },
        bottomBar = {
            // 🌟 Animated Visibility for slide effect
            AnimatedVisibility(
                visible = bottomBarVisible,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
            ) {
                BottomAppBar(
                    actions = {
                        IconButton(onClick = { /* Home Action */ }) { Icon(Icons.Filled.Home, "Home") }
                        IconButton(onClick = { /* Search Action */ }) { Icon(Icons.Filled.Search, "Search") }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { fabClicked = !fabClicked },
                            shape = CutCornerShape(topStart = 24.dp, bottomEnd = 24.dp), // Custom Shape
                            modifier = Modifier.scale(scale) // Bouncy Motion
                        ) {
                            Icon(Icons.Filled.CameraAlt, "Expressive FAB Action")
                        }
                    },
                )
            }
        }
    ) { paddingValues ->
        // 3. SCROLLABLE CONTENT
        val itemsList = (1..50).map { "Content Item $it" }

        LazyColumn(state = scrollState, modifier = Modifier.padding(paddingValues)) {
            item {
                Text(
                    "Scroll Down to hide the bar. Scroll Up to show it. Tap FAB for bounce.",
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(itemsList) { item ->
                ListItem(headlineContent = { Text(item) })
            }
        }
    }
}

/**
 * Helper extension property to determine if the user is scrolling up.
 * Required for the expressive hide/show Bottom App Bar behavior.
 */
@Composable
fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableStateOf(firstVisibleItemIndex) }
    var previousOffset by remember(this) { mutableStateOf(firstVisibleItemScrollOffset) }

    return remember(this) {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                // If index changed, scrolling up if the new index is smaller
                firstVisibleItemIndex < previousIndex
            } else {
                // If index is the same, scrolling up if the offset is smaller
                firstVisibleItemScrollOffset < previousOffset
            }
        }.also {
            previousIndex = firstVisibleItemIndex
            previousOffset = firstVisibleItemScrollOffset
        }.value
    }
}