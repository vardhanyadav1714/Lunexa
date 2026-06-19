package com.twango.lunexa.feature.home.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.twango.lunexa.core.network.dto.AccountDto
import com.twango.lunexa.core.network.dto.CategoryDto
import com.twango.lunexa.core.network.dto.MonthlySummaryDto
import java.math.BigDecimal
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun HomeRoute(
    onLoggedOut: () -> Unit,
    onAuthError: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(state.isLoggedOut) {
        if (state.isLoggedOut) {
            onLoggedOut()
        }
    }

    // Handle authentication errors
    LaunchedEffect(state.errorMessage) {
        val error = state.errorMessage
        if (error != null && (
            error.contains("Authentication failed", ignoreCase = true) ||
            error.contains("401", ignoreCase = true) ||
            error.contains("unauthorized", ignoreCase = true)
        )) {
            onAuthError(error)
        }
    }

    HomeScreen(
        state = state,
        onRefresh = viewModel::load,
        onAccountNameChange = viewModel::onAccountNameChange,
        onOpeningBalanceChange = viewModel::onOpeningBalanceChange,
        onCreateAccount = viewModel::createAccount,
        onTransactionAmountChange = viewModel::onTransactionAmountChange,
        onMerchantChange = viewModel::onMerchantChange,
        onNoteChange = viewModel::onNoteChange,
        onCreateExpense = viewModel::createExpense,
        onBudgetAmountChange = viewModel::onBudgetAmountChange,
        onCreateBudget = viewModel::createBudget,
        onLogout = viewModel::logout
    )
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onAccountNameChange: (String) -> Unit,
    onOpeningBalanceChange: (String) -> Unit,
    onCreateAccount: () -> Unit,
    onTransactionAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCreateExpense: () -> Unit,
    onBudgetAmountChange: (String) -> Unit,
    onCreateBudget: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedDashboardBackdrop(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeaderBar(onLogout = onLogout)

                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }

                state.message?.let {
                    StatusPanel(
                        text = it,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                state.errorMessage?.let {
                    StatusPanel(
                        text = it,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(500)) +
                        slideInVertically(
                            animationSpec = tween(650, easing = FastOutSlowInEasing),
                            initialOffsetY = { it / 5 }
                        )
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SummaryHero(state.summary)
                        InsightStrip(state)
                        FinanceVisualSuite(state)
                        AccountSection(
                            state = state,
                            onAccountNameChange = onAccountNameChange,
                            onOpeningBalanceChange = onOpeningBalanceChange,
                            onCreateAccount = onCreateAccount
                        )
                        ExpenseSection(
                            state = state,
                            onTransactionAmountChange = onTransactionAmountChange,
                            onMerchantChange = onMerchantChange,
                            onNoteChange = onNoteChange,
                            onCreateExpense = onCreateExpense
                        )
                        BudgetSection(
                            state = state,
                            onBudgetAmountChange = onBudgetAmountChange,
                            onCreateBudget = onCreateBudget
                        )
                    }
                }

                Button(
                    onClick = onRefresh,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    } else {
                        Text("Refresh dashboard", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AnimatedDashboardBackdrop(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "dashboard backdrop")
    val drift by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "diagonal drift"
    )

    Canvas(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    colorScheme.background,
                    colorScheme.primaryContainer.copy(alpha = 0.36f),
                    colorScheme.secondaryContainer.copy(alpha = 0.22f),
                    colorScheme.background
                )
            )
        )
    ) {
        val travel = size.width + size.height
        val offset = travel * drift
        repeat(5) { index ->
            val railOffset = offset - travel + (index * travel / 3.2f)
            drawLine(
                color = when (index % 3) {
                    0 -> colorScheme.primary.copy(alpha = 0.07f)
                    1 -> colorScheme.secondary.copy(alpha = 0.06f)
                    else -> colorScheme.tertiary.copy(alpha = 0.055f)
                },
                start = Offset(railOffset, size.height + 80f),
                end = Offset(railOffset + size.height, -80f),
                strokeWidth = 42f
            )
        }
    }
}

@Composable
private fun HeaderBar(onLogout: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "L",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "Lunexa",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Live money command center",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            TextButton(onClick = onLogout) {
                Text("Logout", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SummaryHero(summary: MonthlySummaryDto?) {
    val currency = summary?.currency ?: "INR"
    val income = amountFloat(summary?.incomeTotal)
    val expense = amountFloat(summary?.expenseTotal)
    val cashflow = amountFloat(summary?.netCashflow)
    val totalMovement = max(income + expense, 1f)
    val incomeShare by animateFloatAsState(
        targetValue = (income / totalMovement).coerceIn(0.08f, 0.92f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "income share"
    )
    val motion = rememberInfiniteTransition(label = "hero shimmer")
    val sweep by motion.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hero sweep"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val x = size.width * sweep
                drawLine(
                    color = Color.White.copy(alpha = 0.18f),
                    start = Offset(x - size.height, size.height),
                    end = Offset(x, 0f),
                    strokeWidth = 62f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(x - size.height - 120f, size.height),
                    end = Offset(x - 120f, 0f),
                    strokeWidth = 22f
                )
            }

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatMonth(summary?.month),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.78f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = money(summary?.netCashflow, currency),
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = cashflowTone(cashflow),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }
                    HeroStatusChip(cashflow)
                }

                CashflowRail(progress = incomeShare)

                CashflowSparkline(
                    values = listOf(
                        income,
                        expense,
                        abs(cashflow),
                        amountFloat(summary?.budgetSummary?.spentAmount),
                        amountFloat(summary?.budgetSummary?.remainingAmount)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HeroMetric(
                        label = "Income",
                        value = money(summary?.incomeTotal, currency),
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetric(
                        label = "Expenses",
                        value = money(summary?.expenseTotal, currency),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatusChip(cashflow: Float) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.16f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Text(
            text = if (cashflow >= 0f) "Positive" else "Review",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CashflowRail(progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Income flow",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
            Text(
                text = "Spend flow",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.23f)
        )
    }
}

@Composable
private fun CashflowSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier
) {
    val color = Color.White
    val lineColor = Color.White.copy(alpha = 0.88f)
    val fillColor = Color.White.copy(alpha = 0.10f)
    val points = values.ifEmpty { listOf(0f, 0f, 0f, 0f) }
    val maxValue = max(points.maxOrNull() ?: 0f, 1f)

    Canvas(modifier = modifier) {
        val step = if (points.size == 1) size.width else size.width / (points.size - 1)
        val mapped = points.mapIndexed { index, value ->
            Offset(
                x = step * index,
                y = size.height - ((value / maxValue).coerceIn(0f, 1f) * size.height * 0.72f) - 8.dp.toPx()
            )
        }

        for (index in 0 until mapped.lastIndex) {
            drawLine(
                color = fillColor,
                start = Offset(mapped[index].x, size.height),
                end = mapped[index],
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = lineColor,
                start = mapped[index],
                end = mapped[index + 1],
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        mapped.forEach { point ->
            drawCircle(color = color.copy(alpha = 0.24f), radius = 7.dp.toPx(), center = point)
            drawCircle(color = color, radius = 3.6.dp.toPx(), center = point)
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.15f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InsightStrip(state: HomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InsightTile(
            label = "Accounts",
            value = state.accounts.size.toString(),
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        InsightTile(
            label = "Categories",
            value = state.categories.size.toString(),
            accent = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
        InsightTile(
            label = "Txns",
            value = (state.summary?.transactionCount ?: 0).toString(),
            accent = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InsightTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FinanceVisualSuite(state: HomeUiState) {
    val summary = state.summary
    val currency = summary?.currency ?: "INR"
    val income = amountFloat(summary?.incomeTotal)
    val expenses = amountFloat(summary?.expenseTotal)
    val budgetRemaining = amountFloat(summary?.budgetSummary?.remainingAmount)
    val segments = financeSegments(
        income = income,
        expenses = expenses,
        budgetRemaining = budgetRemaining
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DonutBreakdownCard(
            title = "Monthly money mix",
            subtitle = "Income, expenses, and budget room",
            centerValue = money(summary?.expenseTotal, currency),
            centerLabel = "spent",
            segments = segments
        )

        BudgetGaugeCard(summary = summary)
        AccountBalanceCard(accounts = state.accounts, currency = currency)
        CategoryPulseCard(categories = state.categories, summary = summary)
    }
}

@Composable
private fun DonutBreakdownCard(
    title: String,
    subtitle: String,
    centerValue: String,
    centerLabel: String,
    segments: List<ChartSegment>
) {
    DashboardPanel(
        title = title,
        subtitle = subtitle,
        accent = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                DonutChart(
                    segments = segments,
                    modifier = Modifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = centerValue,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = centerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                segments.forEach { segment ->
                    LegendRow(segment)
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    segments: List<ChartSegment>,
    modifier: Modifier = Modifier
) {
    val total = max(segments.sumOf { it.value.toDouble() }.toFloat(), 1f)
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "donut progress"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = 24.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = Color.White.copy(alpha = 0.62f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        var start = -90f
        segments.forEach { segment ->
            val sweep = (segment.value / total) * 360f * progress
            drawArc(
                color = segment.color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            start += sweep
        }
    }
}

@Composable
private fun LegendRow(segment: ChartSegment) {
    val percent = (segment.share * 100f).roundToInt().coerceAtLeast(1)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(segment.color)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = segment.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$percent% of mix",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetGaugeCard(summary: MonthlySummaryDto?) {
    val currency = summary?.currency ?: "INR"
    val utilization = budgetUtilization(summary) ?: 0f
    val animatedUtilization by animateFloatAsState(
        targetValue = utilization,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "budget gauge"
    )

    DashboardPanel(
        title = "Budget cockpit",
        subtitle = "This month's spending pressure",
        accent = MaterialTheme.colorScheme.secondary
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            SemiCircleGauge(
                progress = animatedUtilization,
                modifier = Modifier.fillMaxSize()
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(animatedUtilization * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "used",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniStat(
                label = "Budget",
                value = money(summary?.budgetSummary?.budgetedAmount, currency),
                modifier = Modifier.weight(1f)
            )
            MiniStat(
                label = "Left",
                value = money(summary?.budgetSummary?.remainingAmount, currency),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SemiCircleGauge(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier) {
        val strokeWidth = 20.dp.toPx()
        val diameter = size.width.coerceAtMost(size.height * 1.95f) - strokeWidth
        val left = (size.width - diameter) / 2f
        val top = 16.dp.toPx()
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = colorScheme.surfaceVariant,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(left, top),
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = when {
                progress >= 0.9f -> colorScheme.error
                progress >= 0.7f -> colorScheme.tertiary
                else -> colorScheme.secondary
            },
            startAngle = 180f,
            sweepAngle = (180f * progress).coerceIn(0f, 180f),
            useCenter = false,
            topLeft = Offset(left, top),
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun AccountBalanceCard(
    accounts: List<AccountDto>,
    currency: String
) {
    DashboardPanel(
        title = "Account pulse",
        subtitle = if (accounts.isEmpty()) "Add an account to unlock balance bars" else "Live balance spread",
        accent = MaterialTheme.colorScheme.tertiary
    ) {
        if (accounts.isEmpty()) {
            EmptyLine("No account added yet.")
        } else {
            val balances = accounts.take(4).map { account ->
                account to abs(amountFloat(account.currentBalance))
            }
            val maxBalance = max(balances.maxOfOrNull { it.second } ?: 0f, 1f)

            balances.forEachIndexed { index, (account, balance) ->
                BalanceBar(
                    account = account,
                    balance = balance,
                    maxBalance = maxBalance,
                    accent = chartPalette(index),
                    currency = currency
                )
            }
        }
    }
}

@Composable
private fun BalanceBar(
    account: AccountDto,
    balance: Float,
    maxBalance: Float,
    accent: Color,
    currency: String
) {
    val progress by animateFloatAsState(
        targetValue = (balance / maxBalance).coerceIn(0.04f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "balance bar"
    )

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = account.type.replace("_", " "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = money(account.currentBalance, currency),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = accent,
            trackColor = accent.copy(alpha = 0.16f)
        )
    }
}

@Composable
private fun CategoryPulseCard(
    categories: List<CategoryDto>,
    summary: MonthlySummaryDto?
) {
    DashboardPanel(
        title = "Spending intelligence",
        subtitle = "Category readiness and largest expense signal",
        accent = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniStat(
                label = "Categories",
                value = categories.size.toString(),
                modifier = Modifier.weight(1f)
            )
            MiniStat(
                label = "Largest",
                value = money(summary?.largestExpense?.amount, summary?.currency ?: "INR"),
                modifier = Modifier.weight(1f)
            )
        }

        if (categories.isNotEmpty()) {
            CategoryTicker(categories.take(6))
        } else {
            EmptyLine("Categories will appear here after the first sync.")
        }
    }
}

@Composable
private fun CategoryTicker(categories: List<CategoryDto>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.take(3).forEachIndexed { index, category ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = chartPalette(index).copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(chartPalette(index))
                    )
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountSection(
    state: HomeUiState,
    onAccountNameChange: (String) -> Unit,
    onOpeningBalanceChange: (String) -> Unit,
    onCreateAccount: () -> Unit
) {
    DashboardPanel(
        title = "Create account",
        subtitle = if (state.accounts.isEmpty()) "Add your first money source" else "Add another balance source",
        accent = MaterialTheme.colorScheme.primary
    ) {
        state.accounts.firstOrNull()?.let { account ->
            AccountPreview(account)
        } ?: EmptyLine("No account added yet.")

        OutlinedTextField(
            value = state.accountName,
            onValueChange = onAccountNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Account name") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        OutlinedTextField(
            value = state.openingBalance,
            onValueChange = onOpeningBalanceChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Opening balance") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        Button(
            onClick = onCreateAccount,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Create cash account", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccountPreview(account: AccountDto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = account.type.replace("_", " "),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = money(account.currentBalance, account.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ExpenseSection(
    state: HomeUiState,
    onTransactionAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCreateExpense: () -> Unit
) {
    DashboardPanel(
        title = "Add expense",
        subtitle = "Capture spending against your first account",
        accent = MaterialTheme.colorScheme.tertiary
    ) {
        OutlinedTextField(
            value = state.transactionAmount,
            onValueChange = onTransactionAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        OutlinedTextField(
            value = state.merchant,
            onValueChange = onMerchantChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Merchant") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        Button(
            onClick = onCreateExpense,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Text("Add expense", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BudgetSection(
    state: HomeUiState,
    onBudgetAmountChange: (String) -> Unit,
    onCreateBudget: () -> Unit
) {
    val utilization = budgetUtilization(state.summary)

    DashboardPanel(
        title = "Create budget",
        subtitle = "Plan this month's category limit",
        accent = MaterialTheme.colorScheme.secondary
    ) {
        if (utilization != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Spent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(utilization * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                LinearProgressIndicator(
                    progress = { utilization },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }
        } else {
            EmptyLine("No budget activity yet.")
        }

        OutlinedTextField(
            value = state.budgetAmount,
            onValueChange = onBudgetAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Budget amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        Button(
            onClick = onCreateBudget,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Text("Create monthly budget", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashboardPanel(
    title: String,
    subtitle: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun StatusPanel(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.12f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f))
            .padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private data class ChartSegment(
    val label: String,
    val value: Float,
    val share: Float,
    val color: Color
)

@Composable
private fun financeSegments(
    income: Float,
    expenses: Float,
    budgetRemaining: Float
): List<ChartSegment> {
    val rawSegments = listOf(
        "Income" to income,
        "Expenses" to expenses,
        "Budget left" to budgetRemaining
    ).filter { it.second > 0f }

    val safeSegments = if (rawSegments.isEmpty()) {
        listOf(
            "Income" to 1f,
            "Expenses" to 1f,
            "Budget left" to 1f
        )
    } else {
        rawSegments
    }

    val total = max(safeSegments.sumOf { it.second.toDouble() }.toFloat(), 1f)
    return safeSegments.mapIndexed { index, (label, value) ->
        ChartSegment(
            label = label,
            value = value,
            share = value / total,
            color = chartPalette(index)
        )
    }
}

@Composable
private fun chartPalette(index: Int): Color {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        Color(0xFFE2A93B),
        Color(0xFF2A9D8F),
        Color(0xFF7C3AED)
    )
    return colors[index % colors.size]
}

private fun money(value: String?, currency: String): String {
    val amount = value?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
    return runCatching {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply {
            this.currency = Currency.getInstance(currency)
            maximumFractionDigits = if (amount.stripTrailingZeros().scale() <= 0) 0 else 2
        }.format(amount)
    }.getOrElse {
        "${amount.toPlainString()} $currency"
    }
}

private fun amountFloat(value: String?): Float =
    value?.trim()?.toFloatOrNull() ?: 0f

private fun budgetUtilization(summary: MonthlySummaryDto?): Float? {
    val percent = summary?.budgetSummary?.utilizationPercent?.toFloatOrNull() ?: return null
    return (percent / 100f).coerceIn(0f, 1f)
}

private fun formatMonth(month: String?): String {
    val parts = month?.split("-").orEmpty()
    val year = parts.getOrNull(0)
    val monthNumber = parts.getOrNull(1)?.toIntOrNull()
    if (year.isNullOrBlank() || monthNumber == null || monthNumber !in 1..12) {
        return "This month"
    }
    val label = DateFormatSymbols(Locale.US).months[monthNumber - 1]
    return "$label $year"
}

private fun cashflowTone(cashflow: Float): String =
    when {
        cashflow > 0f -> "Income is outpacing expenses."
        cashflow < 0f -> "Expenses are ahead this month."
        else -> "Start adding activity to unlock insights."
    }
