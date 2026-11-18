package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CreatorEarningsCalculator
import com.example.data.model.CreatorEarningsSummary
import com.example.data.model.PublicProfile
import com.example.data.model.WithdrawalPayoutMethod
import com.example.data.model.WithdrawalRequest
import com.example.data.model.WithdrawalStatus
import com.example.ui.components.formatCompactCount
import com.example.ui.state.UiState
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.MintTertiary
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun WalletEarningsScreen(
    myProfile: PublicProfile?,
    summary: CreatorEarningsSummary,
    withdrawalsState: UiState<List<WithdrawalRequest>>,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSubmitWithdrawal: (
        amountUsd: Double,
        payoutMethod: WithdrawalPayoutMethod,
        accountHolderName: String,
        upiId: String?,
        bankName: String?,
        accountNumber: String?,
        ifscCode: String?,
        note: String?,
        onDone: () -> Unit
    ) -> Unit,
    onCancelWithdrawal: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    var showWithdrawDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("wallet_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Wallet & Earnings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${myProfile?.username ?: "creator"} • Creator Payouts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Min $${"%.0f".format(CreatorEarningsCalculator.MIN_WITHDRAWAL_USD)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Wallet Balance Hero Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF17132B), Color(0xFF112924))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Available to Withdraw",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Icon(
                                    imageVector = Icons.Filled.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MintTertiary
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = CreatorEarningsCalculator.formatUsd(summary.availableBalanceUsd),
                                style = MaterialTheme.typography.displayLarge,
                                color = MintTertiary,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("wallet_available_balance")
                            )

                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                WalletMetricPill(
                                    label = "Total Earned",
                                    value = CreatorEarningsCalculator.formatUsd(summary.grossEarningsUsd),
                                    modifier = Modifier.weight(1f)
                                )
                                WalletMetricPill(
                                    label = "Pending",
                                    value = CreatorEarningsCalculator.formatUsd(summary.pendingWithdrawnUsd),
                                    modifier = Modifier.weight(1f)
                                )
                                WalletMetricPill(
                                    label = "Withdrawn",
                                    value = CreatorEarningsCalculator.formatUsd(summary.completedWithdrawnUsd),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = { showWithdrawDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CoralPrimary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("withdraw_money_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Payments,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Withdraw Money",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (summary.availableBalanceUsd < CreatorEarningsCalculator.MIN_WITHDRAWAL_USD) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Minimum payout threshold is $1.00. Publish Shorts/Videos to grow views, likes & subscribers.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Earnings Breakdown Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Earnings Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        BreakdownRow(
                            label = "Video Views (${formatCompactCount(summary.totalViews)} views @ $2.50/1K)",
                            amount = CreatorEarningsCalculator.formatUsd(summary.viewsEarningsUsd)
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        BreakdownRow(
                            label = "Engagement Likes (${formatCompactCount(summary.totalLikes)} likes @ $0.02)",
                            amount = CreatorEarningsCalculator.formatUsd(summary.likesEarningsUsd)
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        BreakdownRow(
                            label = "Subscribers (${formatCompactCount(summary.subscribersCount)} subs @ $0.50)",
                            amount = CreatorEarningsCalculator.formatUsd(summary.subsEarningsUsd)
                        )
                    }
                }
            }

            // 3. Withdrawal History
            item {
                Text(
                    text = "Withdrawal Requests",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            when (withdrawalsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CoralPrimary)
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = withdrawalsState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    val requests = withdrawalsState.data
                    if (requests.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = "No Withdrawal Requests Yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Tap 'Withdraw Money' above to request a payout via UPI ID or Bank Transfer.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(requests, key = { it.id }) { req ->
                            WithdrawalHistoryCard(
                                request = req,
                                onCancel = { onCancelWithdrawal(req.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showWithdrawDialog) {
        WithdrawMoneyDialog(
            defaultHolderName = myProfile?.displayName ?: "",
            availableBalanceUsd = summary.availableBalanceUsd,
            isSaving = isSaving,
            onDismiss = { if (!isSaving) showWithdrawDialog = false },
            onConfirm = { amountUsd, method, holder, upi, bankName, accNum, ifsc, note ->
                onSubmitWithdrawal(
                    amountUsd,
                    method,
                    holder,
                    upi,
                    bankName,
                    accNum,
                    ifsc,
                    note
                ) {
                    showWithdrawDialog = false
                }
            }
        )
    }
}

@Composable
private fun WalletMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    amount: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = amount,
            style = MaterialTheme.typography.titleMedium,
            color = MintTertiary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun WithdrawalHistoryCard(
    request: WithdrawalRequest,
    onCancel: () -> Unit
) {
    val method = WithdrawalPayoutMethod.fromWire(request.payoutMethod)
    val status = WithdrawalStatus.fromWire(request.status)
    val statusColor = when (status) {
        WithdrawalStatus.PENDING, WithdrawalStatus.PROCESSING -> AmberSecondary
        WithdrawalStatus.COMPLETED -> MintTertiary
        WithdrawalStatus.REJECTED -> MaterialTheme.colorScheme.error
    }

    val destinationText = if (method == WithdrawalPayoutMethod.UPI) {
        request.upiId.orEmpty().ifBlank { "UPI Transfer" }
    } else {
        val tail = request.accountNumber.orEmpty().takeLast(4).ifBlank { "****" }
        "${request.bankName.orEmpty()} •••• $tail"
    }

    val formattedDate = remember(request.createdAt) {
        request.createdAt?.toDate()?.let { date ->
            SimpleDateFormat("MMM d, h:mm a", Locale.US).format(date)
        } ?: "Just now"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("withdrawal_card_${request.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (method == WithdrawalPayoutMethod.UPI) {
                                Icons.Filled.QrCode2
                            } else {
                                Icons.Filled.AccountBalance
                            },
                            contentDescription = method.label,
                            tint = CoralPrimary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${method.label} • $destinationText",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${request.accountHolderName} • $formattedDate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CreatorEarningsCalculator.formatUsd(request.amountUsd),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = statusColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (status == WithdrawalStatus.PENDING) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("cancel_withdrawal_${request.id}")
                    ) {
                        Text("Cancel Request", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun WithdrawMoneyDialog(
    defaultHolderName: String,
    availableBalanceUsd: Double,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        amountUsd: Double,
        payoutMethod: WithdrawalPayoutMethod,
        accountHolderName: String,
        upiId: String?,
        bankName: String?,
        accountNumber: String?,
        ifscCode: String?,
        note: String?
    ) -> Unit
) {
    var payoutMethod by remember { mutableStateOf(WithdrawalPayoutMethod.UPI) }
    var amountInput by remember {
        mutableStateOf(
            if (availableBalanceUsd >= CreatorEarningsCalculator.MIN_WITHDRAWAL_USD) {
                String.format(Locale.US, "%.2f", availableBalanceUsd)
            } else {
                ""
            }
        )
    }
    var accountHolderName by remember { mutableStateOf(defaultHolderName) }
    var upiId by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var ifscCode by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val parsedAmount = amountInput.trim().toDoubleOrNull() ?: 0.0
    val amountValid = parsedAmount >= CreatorEarningsCalculator.MIN_WITHDRAWAL_USD &&
        parsedAmount <= availableBalanceUsd + 0.0001
    val holderValid = accountHolderName.trim().length >= 2
    val upiValid = Regex("^[^\\s@]+@[^\\s@]+$").matches(upiId.trim())
    val bankValid = bankName.trim().length >= 2 &&
        accountNumber.trim().length in 6..24 &&
        ifscCode.trim().length in 4..20

    val canSubmit = !isSaving &&
        amountValid &&
        holderValid &&
        when (payoutMethod) {
            WithdrawalPayoutMethod.UPI -> upiValid
            WithdrawalPayoutMethod.BANK_TRANSFER -> bankValid
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Withdraw Money") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Available Balance: ${CreatorEarningsCalculator.formatUsd(availableBalanceUsd)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MintTertiary,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = payoutMethod == WithdrawalPayoutMethod.UPI,
                        onClick = { payoutMethod = WithdrawalPayoutMethod.UPI },
                        label = { Text("UPI ID") },
                        leadingIcon = {
                            Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("payout_method_upi")
                    )
                    FilterChip(
                        selected = payoutMethod == WithdrawalPayoutMethod.BANK_TRANSFER,
                        onClick = { payoutMethod = WithdrawalPayoutMethod.BANK_TRANSFER },
                        label = { Text("Bank Transfer") },
                        leadingIcon = {
                            Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("payout_method_bank")
                    )
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { raw ->
                        if (raw.length <= 12) amountInput = raw
                    },
                    label = { Text("Amount (USD)") },
                    placeholder = { Text("Min 1.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountInput.isNotBlank() && !amountValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("withdraw_amount_input")
                )

                if (availableBalanceUsd >= CreatorEarningsCalculator.MIN_WITHDRAWAL_USD) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("25%" to 0.25, "50%" to 0.50, "Max" to 1.0).forEach { (label, frac) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val target = (availableBalanceUsd * frac).coerceAtLeast(CreatorEarningsCalculator.MIN_WITHDRAWAL_USD)
                                        .coerceAtMost(availableBalanceUsd)
                                    amountInput = String.format(Locale.US, "%.2f", target)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Earn at least $1.00 from video views, likes, or subscribers before sending a withdrawal request.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                OutlinedTextField(
                    value = accountHolderName,
                    onValueChange = { if (it.length <= 80) accountHolderName = it },
                    label = { Text("Account Holder Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("withdraw_holder_input")
                )

                if (payoutMethod == WithdrawalPayoutMethod.UPI) {
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { if (it.length <= 80) upiId = it },
                        label = { Text("UPI ID") },
                        placeholder = { Text("yourname@okaxis") },
                        singleLine = true,
                        isError = upiId.isNotBlank() && !upiValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_upi_input")
                    )
                } else {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { if (it.length <= 80) bankName = it },
                        label = { Text("Bank Name") },
                        placeholder = { Text("State Bank of India / HDFC") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_bank_name_input")
                    )
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { if (it.length <= 24) accountNumber = it },
                        label = { Text("Account Number") },
                        placeholder = { Text("6 to 24 digits") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_account_number_input")
                    )
                    OutlinedTextField(
                        value = ifscCode,
                        onValueChange = { if (it.length <= 20) ifscCode = it.uppercase(Locale.US) },
                        label = { Text("IFSC / Routing Code") },
                        placeholder = { Text("SBIN0001234") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_ifsc_input")
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 160) note = it },
                    label = { Text("Note (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("withdraw_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        parsedAmount,
                        payoutMethod,
                        accountHolderName.trim(),
                        upiId.trim().ifBlank { null },
                        bankName.trim().ifBlank { null },
                        accountNumber.trim().ifBlank { null },
                        ifscCode.trim().ifBlank { null },
                        note.trim().ifBlank { null }
                    )
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                modifier = Modifier.testTag("confirm_withdraw_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Send Request")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}
