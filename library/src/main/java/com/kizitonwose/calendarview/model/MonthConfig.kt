package com.kizitonwose.calendarview.model

import com.kizitonwose.calendarview.utils.NO_INDEX
import com.kizitonwose.calendarview.utils.next
import com.kizitonwose.calendarview.utils.yearMonth
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth
import org.threeten.bp.temporal.WeekFields

data class MonthConfig(
    val outDateStyle: OutDateStyle,
    val inDateStyle: InDateStyle,
    val maxRowCount: Int,
    val startMonth: YearMonth,
    val endMonth: YearMonth,
    val firstDayOfWeek: DayOfWeek,
    val hasBoundaries: Boolean
) {

    val months: List<CalendarMonth> by lazy lazy@{
        return@lazy if (hasBoundaries) {
            generateBoundedMonths(
                startMonth, endMonth, firstDayOfWeek,
                maxRowCount, inDateStyle, outDateStyle
            )
        } else {
            generateUnboundedMonths(startMonth, endMonth, maxRowCount)
        }
    }

    companion object {

        fun generateBoundedMonths(
            startMonth: YearMonth,
            endMonth: YearMonth,
            firstDayOfWeek: DayOfWeek,
            maxRowCount: Int,
            inDateStyle: InDateStyle,
            outDateStyle: OutDateStyle
        ): List<CalendarMonth> {
            val months = mutableListOf<CalendarMonth>()
            var nextMonth = startMonth
            while (nextMonth <= endMonth) {
                months.addAll(generateBoundedMonth(nextMonth, firstDayOfWeek, maxRowCount, inDateStyle, outDateStyle))
                nextMonth = nextMonth.next
            }
            return months
        }

        /**
         * This generates the necessary number of [CalendarMonth] instances for a [YearMonth].
         * A [YearMonth] will have multiple [CalendarMonth] instances if the [maxRowCount] is
         * less than 6. Each [CalendarMonth] will hold just enough [CalendarDay] instances(weekDays)
         * to fit in the [maxRowCount].
         */
        fun generateBoundedMonth(
            yearMonth: YearMonth,
            firstDayOfWeek: DayOfWeek,
            maxRowCount: Int,
            inDateStyle: InDateStyle,
            outDateStyle: OutDateStyle
        ): List<CalendarMonth> {
            val year = yearMonth.year
            val month = yearMonth.monthValue

            val thisMonthDays = (1..yearMonth.lengthOfMonth()).map {
                CalendarDay(LocalDate.of(year, month, it), DayOwner.THIS_MONTH)
            }.toMutableList()

            val weekDaysGroup = if (inDateStyle == InDateStyle.NONE) {
                // Group days by 7, first day shown on the month will be day 1.
                val groupBySeven = mutableListOf<List<CalendarDay>>()
                while (thisMonthDays.isNotEmpty()) {
                    val nextRow = thisMonthDays.take(7)
                    groupBySeven.add(nextRow)
                    thisMonthDays.removeAll(nextRow)
                }
                groupBySeven
            } else {
                // Group days by week of month so we can add the in dates if necessary.
                val weekOfMonthField = WeekFields.of(firstDayOfWeek, 1).weekOfMonth()
                val groupByWeekOfMonth = thisMonthDays.groupBy { it.date.get(weekOfMonthField) }.values.toMutableList()

                // Add in-dates if necessary
                val firstWeek = groupByWeekOfMonth.first()
                if (firstWeek.size < 7) {
                    val previousMonth = yearMonth.minusMonths(1)
                    val inDates = (1..previousMonth.lengthOfMonth()).toList()
                        .takeLast(7 - firstWeek.size).map {
                            CalendarDay(
                                LocalDate.of(previousMonth.year, previousMonth.month, it),
                                DayOwner.PREVIOUS_MONTH
                            )
                        }
                    groupByWeekOfMonth[0] = inDates + firstWeek
                }
                groupByWeekOfMonth
            }


            if (outDateStyle == OutDateStyle.END_OF_ROW || outDateStyle == OutDateStyle.END_OF_GRID) {
                // Add out-dates for the last row.
                val nextMonth = yearMonth.plusMonths(1)
                val lastWeek = weekDaysGroup.last()
                if (lastWeek.size < 7) {
                    val outDates = (1..7 - lastWeek.size).map {
                        CalendarDay(LocalDate.of(nextMonth.year, nextMonth.month, it), DayOwner.NEXT_MONTH)
                    }
                    weekDaysGroup[weekDaysGroup.lastIndex] = lastWeek + outDates
                }

                // Add more rows to form a 6 x 7 grid
                if (outDateStyle == OutDateStyle.END_OF_GRID) {
                    while (weekDaysGroup.size < 6) {
                        val lastDay = weekDaysGroup.last().last()
                        val nextRowDates = (1..7).map {
                            val dayValue = if (lastDay.owner == DayOwner.THIS_MONTH) it else it + lastDay.day
                            CalendarDay(LocalDate.of(nextMonth.year, nextMonth.month, dayValue), DayOwner.NEXT_MONTH)
                        }
                        weekDaysGroup.add(nextRowDates)
                    }
                }
            }

            // Group rows by maxRowCount into CalendarMonth classes.
            val calendarMonths = mutableListOf<CalendarMonth>()
            val div = weekDaysGroup.count() / maxRowCount
            val rem = weekDaysGroup.count() % maxRowCount
            val numberOfSameMonth = if (rem == 0) div else div + 1
            while (weekDaysGroup.isNotEmpty()) {
                val monthDays = weekDaysGroup.take(maxRowCount)
                calendarMonths.add(CalendarMonth(yearMonth, monthDays, calendarMonths.count(), numberOfSameMonth))
                weekDaysGroup.removeAll(monthDays)
            }
            return calendarMonths
        }

        fun generateUnboundedMonths(
            startMonth: YearMonth,
            endMonth: YearMonth,
            maxRowCount: Int
        ): List<CalendarMonth> {
            val days = mutableListOf<CalendarDay>()
            var nextMonth = startMonth
            while (nextMonth <= endMonth) {
                days.addAll(
                    (1..nextMonth.lengthOfMonth()).map {
                        CalendarDay(LocalDate.of(nextMonth.year, nextMonth.month, it), DayOwner.THIS_MONTH)
                    }
                )
                nextMonth = nextMonth.next
            }

            val daysGroup = mutableListOf<List<CalendarDay>>()
            while (days.isNotEmpty()) {
                val sevenDays = days.take(7)
                daysGroup.add(sevenDays)
                days.removeAll(sevenDays)
            }

            val calendarMonths = mutableListOf<CalendarMonth>()
            while (daysGroup.isNotEmpty()) {
                val monthDays = daysGroup.take(maxRowCount)
                calendarMonths.add(CalendarMonth(monthDays.first().first().date.yearMonth, monthDays, NO_INDEX, NO_INDEX))
                daysGroup.removeAll(monthDays)
            }
            return calendarMonths
        }
    }
}
