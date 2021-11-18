//package no.nav.eessi.pensjon.personidentifisering
//
//import javax.annotation.PostConstruct
//
//class Validering {
//
//
//    private val landFormateringMap: MutableMap<EessiCountry, Formatering> = HashMap<EessiCountry, Formatering>()
//
//    @PostConstruct
//    fun postSetup() {
//        landFormateringMap[EessiCountry.BELGIUM] = Formatering { ident: String ->
//            ident = ident.trim()
//            ident = ident.replace("[-:./\\s]".toRegex(), "")
//            if (harAntallSiffer(ident, 11)) {
//                return@Formatering ident.substring(0, 6) + "-" + ident.substring(6, 9) + "-" + ident.substring(9)
//            }
//            ident
//        }
//        landFormateringMap[EessiCountry.DENMARK] = Formatering { ident: String? ->
//            ident = ident!!.trim()
//            if (ident != null && ident.length > 3 && "CPR".equals(ident.substring(0, 3), ignoreCase = true)) {
//                ident = ident.substring(3).trim()
//            }
//            if (harAntallSiffer(ident, 10)) {
//                return@put ident.substring(0, 6) + "-" + ident.substring(6)
//            }
//            ident
//        }
//        landFormateringMap[EessiCountry.ICELAND] = Formatering { ident: String ->
//            ident = ident.trim()
//            if (harAntallSiffer(ident, 10)) {
//                return@put ident.substring(0, 6) + "-" + ident.substring(6)
//            }
//            ident
//        }
//        landFormateringMap[EessiCountry.FINLAND] = Formatering { ident: String ->
//            ident = ident.trim()
//            ident = ident.replace("[-:./\\s]".toRegex(), "")
//            if (10 == ident.length) {
//                return@put ident.substring(0, 6) + "-" + ident.substring(6)
//            }
//            ident
//        }
//        landFormateringMap[EessiCountry.NETHERLANDS] = Formatering { ident: String ->
//            ident = ident.trim()
//            if (harAntallSiffer(ident, 9)) {
//                return@put ident.substring(0, 4) + "." + ident.substring(4, 6) + "." + ident.substring(6)
//            }
//            ident
//        }
//        landFormateringMap[EessiCountry.SWEDEN] = Formatering { ident: String ->
//            ident = ident.trim()
//            // Fjerner to første sifre fra firesifret årstall i svensk id
//            if (ident.length > 11 && ident.replace("[-./\\s]".toRegex(), "").length > 11 && ("19" == ident.substring(0, 2) || "20" == ident.substring(
//                    0, 2
//                ))
//            ) {
//                ident = ident.substring(2)
//            }
//            ident = ident.replace("[-:./\\s]".toRegex(), "")
//            if (harAntallSiffer(ident, 10)) {
//                return@put ident.substring(0, 6) + "-" + ident.substring(6)
//            }
//            ident
//        }
//        landFormateringMap[EessiCountry.NORWAY] = Formatering { ident: String? ->
//            ident = ident!!.trim()
//            if (ident != null && ident.length > 7 && "DNUMMER".equals(ident.substring(0, 7), ignoreCase = true)) {
//                ident = ident.substring(7).trim()
//            } else if (ident != null && ident.length > 8 && "D-NUMMER".equals(ident.substring(0, 8), ignoreCase = true)) {
//                ident = ident.substring(8).trim()
//            } else if (ident != null && ident.length > 2 && "NO".equals(ident.substring(0, 2), ignoreCase = true)) {
//                ident = ident.substring(2).trim()
//            } else if (ident != null && ident.length > 2 && "ID".equals(ident.substring(0, 2), ignoreCase = true)) {
//                ident = ident.substring(2).trim()
//            } else if (ident != null && ident.length > 1 && "D".equals(ident.substring(0, 1), ignoreCase = true)) {
//                ident = ident.substring(1).trim()
//            }
//            val splits = ident.split("[/\\\\]").toTypedArray()
//            if (splits.size > 1) {
//                var antallFnr = 0
//                var fnr: String? = ""
//                for (split in splits) {
//                    if (split != null) {
//                        split = split.replace("[-:.\\s]".toRegex(), "")
//                    }
//                    if (harAntallSiffer(split, 11)) {
//                        antallFnr++
//                        fnr = split
//                    }
//                }
//                if (antallFnr == 1) {
//                    ident = fnr
//                } else if (antallFnr == 0) {
//                    ident = splittWhitespace(ident)
//                }
//            } else {
//                ident = splittWhitespace(ident)
//            }
//            ident = ident!!.replace("[-:./\\s]".toRegex(), "")
//            ident
//        }
//    }
//
//    private fun splittWhitespace(ident: String?): String? {
//        var ident = ident
//        val splits2 = ident!!.split("[\\s]").toTypedArray()
//        var antallFnr2 = 0
//        var fnr2: String? = ""
//        for (split in splits2) {
//            if (split != null) {
//                split = split.replace("[-:./\\s]".toRegex(), "")
//            }
//            if (harAntallSiffer(split, 11)) {
//                antallFnr2++
//                fnr2 = split
//            }
//        }
//        if (antallFnr2 == 1) {
//            ident = fnr2
//        }
//        return ident
//    }
//
//    fun formaterIdent(ident: String?, land: EessiCountry): String? {
//        return if (landFormateringMap.containsKey(land)) {
//            landFormateringMap[land]!!.formater(ident)
//        } else ident?.replace("[-:./\\s]".toRegex(), "") ?: ident
//    }
//
//    fun formaterFnr(ident: String?): String? {
//        return formaterIdent(ident, EessiCountry.NORWAY)
//    }
//
//    fun harAntallSiffer(identifikator: String?, antallSiffer: Int): Boolean {
//        val identIsDigits = identifikator?.all { Character.isDigit(it) } ?: false
//        return identifikator != null && identIsDigits && antallSiffer == identifikator.length
//    }
//
//    interface Formatering {
//        fun formater(ident: String?): String?
//    }
//
//
//
//
//}