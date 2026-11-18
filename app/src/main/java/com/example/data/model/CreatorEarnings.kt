package com.example.data.model

import java.util.Locale

data class CreatorEarningsSummary(
    val totalViews: Long,
    val shortsViews: Long,
    val fullViews: Long,
    val totalLikes: Long,
    val subscribersCount: Int,
    val followersCount: Int,
    val viewsEarningsUsd: Double,
    val likesEarningsUsd: Double,
    val subsEarningsUsd: Double,
    val grossEarningsUsd: Double,
    val pendingWithdrawnUsd: Double,
    val completedWithdrawnUsd: Double,
    val availableBalanceUsd: Double
)

object CreatorEarningsCalculator {
    const val RPM_PER_1K_VIEWS = 2.50
    const val BONUS_PER_LIKE = 0.02
    const val BONUS_PER_SUBSCRIBER = 0.50
    const val MIN_WITHDRAWAL_USD = 1.00

    fun compute(
        myShorts: List<FeedVideoItem>,
        myFullVideos: List<FeedVideoItem>,
        followersCount: Int,
        subscribersCount: Int,
        withdrawals: List<WithdrawalRequest> = emptyList()
    ): CreatorEarningsSummary {
        val shortsViews = myShorts.sumOf { it.post.viewsCount }
        val fullViews = myFullVideos.sumOf { it.post.viewsCount }
        val totalViews = shortsViews + fullViews
        val totalLikes = (myShorts + myFullVideos).sumOf { it.likeCount.toLong() }

        val viewsEarnings = (totalViews / 1000.0) * RPM_PER_1K_VIEWS
        val likesEarnings = totalLikes * BONUS_PER_LIKE
        val subsEarnings = subscribersCount * BONUS_PER_SUBSCRIBER
        val grossEarnings = viewsEarnings + likesEarnings + subsEarnings

        val pendingWithdrawn = withdrawals
            .filter {
                val st = WithdrawalStatus.fromWire(it.status)
                st == WithdrawalStatus.PENDING || st == WithdrawalStatus.PROCESSING
            }
            .sumOf { it.amountUsd }

        val completedWithdrawn = withdrawals
            .filter { WithdrawalStatus.fromWire(it.status) == WithdrawalStatus.COMPLETED }
            .sumOf { it.amountUsd }

        val reservedOrPaid = pendingWithdrawn + completedWithdrawn
        val availableBalance = (grossEarnings - reservedOrPaid).coerceAtLeast(0.0)

        return CreatorEarningsSummary(
            totalViews = totalViews,
            shortsViews = shortsViews,
            fullViews = fullViews,
            totalLikes = totalLikes,
            subscribersCount = subscribersCount,
            followersCount = followersCount,
            viewsEarningsUsd = viewsEarnings,
            likesEarningsUsd = likesEarnings,
            subsEarningsUsd = subsEarnings,
            grossEarningsUsd = grossEarnings,
            pendingWithdrawnUsd = pendingWithdrawn,
            completedWithdrawnUsd = completedWithdrawn,
            availableBalanceUsd = availableBalance
        )
    }

    fun formatUsd(amount: Double): String =
        String.format(Locale.US, "$%.2f", amount.coerceAtLeast(0.0))
}
