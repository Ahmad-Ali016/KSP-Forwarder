package com.kspay.forwarder.di

/**
 * Root dependency container (manual DI, no Hilt/Dagger — keeps the APK lean for KPay vetting).
 * Populated incrementally as crypto/, kpay/, data/, net/, sync/ are built in later phases.
 */
interface AppContainer

class DefaultAppContainer : AppContainer
