package timetablescheduler.adapters.web

import timetablescheduler.application.MasterDataUseCases

/** The use cases the web adapter calls; built once from the ports by the composition root or a test. */
class WebDependencies(val useCases: MasterDataUseCases)

/** [sessionSignKey] signs the session cookie; [devMode] allows non-`Secure` cookies over plain http. */
class WebSettings(val devMode: Boolean, val sessionSignKey: ByteArray)
