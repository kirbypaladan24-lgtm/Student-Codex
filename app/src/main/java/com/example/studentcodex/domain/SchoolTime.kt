package com.example.studentcodex.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The university's calendar zone.
 *
 * Every deadline in Student Codex is a ParSU wall-clock date first: "due
 * Nov 6" means November 6 in the Philippines. Due dates are therefore
 * interpreted in this fixed zone instead of ZoneId.systemDefault() — a
 * device (or emulator) set to GMT/UTC would otherwise save "Nov 6,
 * 11:59 PM" as 23:59 UTC, eight hours late for Manila, and the activity
 * would land on Nov 7 for every viewer in the Philippines (teacher
 * dashboard, classmates' phones). Anchoring to the school zone keeps the
 * picked date wherever the device happens to be.
 */
val SchoolZone: ZoneId = ZoneId.of("Asia/Manila")

/** End-of-day deadline for a due DATE — the intuitive reading of "due Friday". */
fun LocalDate.toDueInstant(): Instant = atTime(23, 59).atZone(SchoolZone).toInstant()
