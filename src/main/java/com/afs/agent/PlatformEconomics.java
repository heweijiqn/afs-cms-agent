package com.afs.agent;

import com.afs.controller.SiteController;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Deterministic, platform-authentic overseas AFS (AdSense for Search / RSOC) content-arbitrage
 * domain catalog and mathematical metrics generator shared by local migrations and demo seeding.
 */
final class PlatformEconomics {
    static final int SITE_COUNT = 12;
    static final int SLOTS_PER_SITE = 20;
    static final int ACTIVE_DAYS = 14;
    static final String PARTNER = "AFS Media";

    record OperatorSpec(String name, String desc, List<String> sites) {}

    record AccountSpec(int siteIndex, String site, String platform, String name, String accountId, String operator) {}

    record RuleSpec(String key, String name, String creator, String platform, String action,
                    String condition, String budgetAction, int budgetPercent, int actionTime) {}

    record Subtopic(String slug, String keyword, String titleEn, String titleDe, String titleFr,
                    String leadEn, String leadDe, String leadFr, List<String> copyEn, List<String> copyDe, List<String> copyFr) {}

    record VerticalSpec(int siteIndex, String categorySlug, String categoryLabel, String platform,
                        double rpmFactor, List<Subtopic> subtopics) {}

    record CountrySpec(String code, String lang, String locale, double cpcMult, double rpcMult, double volMult) {}

    record CreativeSpec(String type, String dimension, String ratio, int duration, int sizeBytes,
                        String template, String ext) {}

    record DailyRow(LocalDate date, long adImpressions, long adClicks, long adConversions,
                    BigDecimal adSpend, long funnelImpressions, long funnelClicks,
                    long serpImpressions, long afsClicks, BigDecimal grossRevenue,
                    double afsRpc, double afsCtr, double funnelRpm) {}

    record CampaignTotals(long adImpressions, long adClicks, long adConversions,
                          BigDecimal adSpend, BigDecimal grossRevenue, BigDecimal profit, double roas,
                          BigDecimal suggestedDailyBudget) {}

    static final List<OperatorSpec> OPERATORS = List.of(
            new OperatorSpec("alex.chen", "北美 TikTok 买量负责人", List.of("cccooperate", "askweai", "vvtechai")),
            new OperatorSpec("sarah.lin", "Meta 信息流增长经理", List.of("superraranker", "magictechai", "bitsxspace")),
            new OperatorSpec("david.wu", "Google 搜索投放经理", List.of("valibooks", "infozoneai", "buildmindai")),
            new OperatorSpec("emily.zhao", "Taboola 原生内容优化师", List.of("niceranker", "bestreplyai", "wiseinfoai")),
            new OperatorSpec("kevin.zhou", "北美高客单垂直投放师", List.of("cccooperate", "valibooks", "vvtechai")),
            new OperatorSpec("claire.huang", "欧洲德法市场投放专员", List.of("niceranker", "superraranker", "magictechai")),
            new OperatorSpec("ryan.li", "自动化规则与风控运营", List.of("askweai", "bestreplyai", "infozoneai")),
            new OperatorSpec("sophia.wang", "创意素材与落地页专家", List.of("wiseinfoai", "buildmindai", "bitsxspace"))
    );

    static final List<AccountSpec> AD_ACCOUNTS = List.of(
            new AccountSpec(0, "cccooperate", "TikTok", "TT-US-HealthFitness-01", "7384910284751029001", "alex.chen"),
            new AccountSpec(1, "niceranker", "Taboola", "TB-Native-SmartHome-01", "1482910401", "emily.zhao"),
            new AccountSpec(2, "valibooks", "Google", "GG-Search-TravelCard-01", "6428910301", "david.wu"),
            new AccountSpec(3, "superraranker", "Facebook", "Meta-Tier1-Nutrition-01", "1084927361040001", "sarah.lin"),
            new AccountSpec(4, "askweai", "TikTok", "TT-Tier1-ErgoOffice-02", "7384910284751029002", "alex.chen"),
            new AccountSpec(5, "bestreplyai", "Taboola", "TB-Native-SolarPower-02", "1482910402", "emily.zhao"),
            new AccountSpec(6, "infozoneai", "Google", "GG-Search-HomeStorage-02", "6428910302", "david.wu"),
            new AccountSpec(7, "magictechai", "Facebook", "Meta-Tier1-OnlineEdu-02", "1084927361040002", "sarah.lin"),
            new AccountSpec(8, "vvtechai", "TikTok", "TT-US-PersonalFin-03", "7384910284751029003", "kevin.zhou"),
            new AccountSpec(9, "wiseinfoai", "Taboola", "TB-Native-SolarRebate-03", "1482910403", "claire.huang"),
            new AccountSpec(10, "buildmindai", "Google", "GG-Search-AutoEV-03", "6428910303", "david.wu"),
            new AccountSpec(11, "bitsxspace", "Facebook", "Meta-Tier1-DentalCare-03", "1084927361040003", "sarah.lin")
    );

    static final List<RuleSpec> RULES = List.of(
            new RuleSpec("tt-stop-loss", "TikTok 亏损止损保护", "alex.chen", "TikTok",
                    "CHANGE_BUDGET", "AD_SPEND >= 80 && ROI < 0.85", "DECREASE", 10, 10),
            new RuleSpec("tt-scale", "TikTok 高回报阶梯放量", "alex.chen", "TikTok",
                    "CHANGE_BUDGET", "AD_SPEND >= 100 && ROI >= 1.45", "INCREASE", 10, 12),
            new RuleSpec("tt-zero", "TikTok 零收益熔断停投", "ryan.li", "TikTok",
                    "CLOSE_CAMPAIGN", "AD_SPEND >= 60 && REVENUE <= 0", "", 0, 8),
            new RuleSpec("fb-reduce", "Meta 低回报降预算", "sarah.lin", "Facebook",
                    "CHANGE_BUDGET", "AD_SPEND >= 90 && ROI < 0.95", "DECREASE", 10, 10),
            new RuleSpec("fb-scale", "Meta 达标稳健扩量", "sarah.lin", "Facebook",
                    "CHANGE_BUDGET", "AD_SPEND >= 120 && ROI >= 1.40", "INCREASE", 10, 14),
            new RuleSpec("fb-profit-guard", "Meta 单日亏损保护", "ryan.li", "Facebook",
                    "CHANGE_BUDGET", "AD_SPEND >= 80 && PROFIT <= -35", "DECREASE", 8, 16),
            new RuleSpec("gg-scale", "Google 高ROI放量护栏", "david.wu", "Google",
                    "CHANGE_BUDGET", "AD_SPEND >= 120 && ROI >= 1.45", "INCREASE", 8, 11),
            new RuleSpec("gg-reduce", "Google 低效预算收缩", "david.wu", "Google",
                    "CHANGE_BUDGET", "AD_SPEND >= 100 && ROI < 1.00", "DECREASE", 8, 13),
            new RuleSpec("gg-zero", "Google 零转化关停保护", "ryan.li", "Google",
                    "CLOSE_CAMPAIGN", "AD_SPEND >= 75 && REVENUE <= 0", "", 0, 9),
            new RuleSpec("tb-reduce", "Taboola 原生止损降量", "emily.zhao", "Taboola",
                    "CHANGE_BUDGET", "AD_SPEND >= 65 && ROI < 0.90", "DECREASE", 10, 10),
            new RuleSpec("tb-scale", "Taboola 高RPM放量规则", "emily.zhao", "Taboola",
                    "CHANGE_BUDGET", "AD_SPEND >= 80 && ROI >= 1.38", "INCREASE", 10, 15),
            new RuleSpec("cold-start", "冷启动保守控量护栏", "ryan.li", "ALL",
                    "CHANGE_BUDGET", "AD_SPEND >= 50 && ROI < 0.95", "DECREASE", 5, 9)
    );

    // Weighted Tier-1/Tier-2 distribution across the 20 slots per site: 7 US, 3 GB, 3 CA, 3 AU, 2 DE, 2 FR.
    private static final List<CountrySpec> SLOT_COUNTRIES = List.of(
            new CountrySpec("US", "en", "en-US", 1.24, 1.30, 1.40),
            new CountrySpec("US", "en", "en-US", 1.24, 1.30, 1.35),
            new CountrySpec("GB", "en", "en-GB", 0.98, 1.03, 1.05),
            new CountrySpec("CA", "en", "en-CA", 1.02, 1.07, 1.00),
            new CountrySpec("AU", "en", "en-AU", 1.06, 1.12, 0.96),
            new CountrySpec("US", "en", "en-US", 1.24, 1.30, 1.30),
            new CountrySpec("DE", "de", "de-DE", 0.84, 0.87, 0.88),
            new CountrySpec("US", "en", "en-US", 1.24, 1.30, 1.28),
            new CountrySpec("FR", "fr", "fr-FR", 0.78, 0.81, 0.82),
            new CountrySpec("GB", "en", "en-GB", 0.98, 1.03, 1.02),
            new CountrySpec("US", "en", "en-US", 1.24, 1.30, 1.32),
            new CountrySpec("CA", "en", "en-CA", 1.02, 1.07, 0.98),
            new CountrySpec("AU", "en", "en-AU", 1.06, 1.12, 0.94),
            new CountrySpec("US", "en", "en-US", 1.24, 1.30, 1.25),
            new CountrySpec("DE", "de", "de-DE", 0.84, 0.87, 0.85),
            new CountrySpec("US", "en", "en-US", 1.24, 1.30, 1.35),
            new CountrySpec("GB", "en", "en-GB", 0.98, 1.03, 1.00),
            new CountrySpec("CA", "en", "en-CA", 1.02, 1.07, 0.95),
            new CountrySpec("FR", "fr", "fr-FR", 0.78, 0.81, 0.80),
            new CountrySpec("AU", "en", "en-AU", 1.06, 1.12, 0.92)
    );

    static final List<String> REJECTION_REASONS = List.of(
            "落地页关键词与广告文案相关性不足，需补充产品参数对比模块",
            "素材文字占比偏高（>20%），建议更换原生实拍场景封面图",
            "广告标题含绝对化表述，需调整为客观价格区间与评测引导语",
            "缺少地区合规免责声明与价格参考说明，请完善落地页页脚"
    );

    static final List<VerticalSpec> VERTICALS = List.of(
            new VerticalSpec(0, "home-fitness", "Home Fitness & Health", "TikTok", 1.05, List.of(
                    sub("FoldableTreadmill", "best foldable treadmill apartment",
                            "Best Foldable Treadmills for Small Apartments (2026 Tested)",
                            "Klappbare Laufbänder für kleine Wohnungen im Test 2026",
                            "Meilleurs Tapis de Course Pliables pour Appartement 2026",
                            "We tested 9 compact walking pads and folding treadmills for noise level, belt cushioning, and real storage footprint.",
                            "Wir haben 9 kompakte Walking-Pads und klappbare Laufbänder auf Lautstärke, Dämpfung und Platzbedarf geprüft.",
                            "Comparatif de 9 tapis de marche pliables testés sur le niveau sonore, l'amorti et le rangement sous le canapé.",
                            List.of("Compact Treadmills That Slide Right Under Your Bed", "Compare 2026 Quiet Folding Treadmills Under $499", "Top Rated Home Walking Pads — See Side-by-Side Specs"),
                            List.of("Leise klappbare Laufbänder fürs Homeoffice 2026", "Platzsparende Walking-Pads im Preis-Leistungs-Vergleich", "Aktuelle Testsieger für kleine Wohnungen entdecken"),
                            List.of("Tapis de Course Pliables Silencieux — Comparatif 2026", "Les Meilleurs Tapis de Marche Compacts à Moins de 450 €", "Comparez Bruit, Amorti et Garantie Avant d'Acheter")),
                    sub("SmartSleepRing", "smart ring sleep tracker comparison",
                            "Smart Rings vs. Fitness Watches: Sleep Tracking Accuracy Guide",
                            "Smart Rings im Vergleich 2026: Schlaftracking ohne Abo?",
                            "Bagues Connectées 2026 : Comparatif Sommeil et Sans Abonnement",
                            "Compare battery life, subscription fees, and HRV sleep accuracy across the leading 2026 smart health rings.",
                            "Akkulaufzeit, monatliche Gebühren und HRV-Messgenauigkeit der führenden Smart Rings im direkten Überblick.",
                            "Autonomie, frais d'abonnement et précision du suivi du sommeil : le guide complet des bagues connectées.",
                            List.of("Tired of Sleeping With a Bulky Watch? Compare Smart Rings", "2026 Smart Health Rings With Zero Monthly Fees", "See Battery Life & Sleep Score Accuracy Side by Side"),
                            List.of("Smart Rings ohne monatliches Abo im Vergleich", "Schlaf- & HRV-Tracking 2026: Welcher Ring lohnt sich?", "Akkulaufzeit und Sensoren im übersichtlichen Test"),
                            List.of("Bagues Connectées Sans Abonnement : Le Top 5 de 2026", "Suivi du Sommeil Ultra-Léger : Comparez les Modèles", "Autonomie 7 Jours et Capteurs Santé en Détail")),
                    sub("AdjustableDumbbells", "adjustable dumbbells home gym",
                            "Space-Saving Adjustable Dumbbells Worth Buying in 2026",
                            "Verstellbare Kurzhanteln fürs Heimtraining: Kaufberatung 2026",
                            "Haltères Réglables Gain de Place : Guide d'Achat 2026",
                            "Replace an entire 15-pair rack with quick-dial adjustable dumbbells—our breakdown of weight range, grip, and durability.",
                            "Ein kompaktes Hantelset ersetzt bis zu 15 Einzelpaare: Gewichtsstufen, Mechanik und Stabilität im Praxisvergleich.",
                            "Remplacez tout un rack par une paire d'haltères réglables : sécurité du mécanisme, poids max et durabilité.",
                            List.of("One Pair Replaces 15 Dumbbells — Compare Top 2026 Sets", "Quick-Dial Home Gym Weights Reviewed for Grip & Safety", "Save Space & Money on Your Home Strength Setup"),
                            List.of("Platzsparende Systemhanteln im Direktvergleich", "Schnellverstellbare Kurzhanteln bis 32 kg getestet", "Heim-Fitnessstudio kompakt einrichten — Top-Modelle"),
                            List.of("Un Seul Duo Remplace 15 Haltères : Comparez les Prix", "Haltères Réglables Rapides : Tests de Solidité 2026", "Optimisez Votre Home Gym Sans Encombrer le Salon")),
                    sub("RedLightTherapy", "red light therapy panel home",
                            "At-Home Red Light Therapy Panels: Wavelength & Price Guide",
                            "Rotlichttherapie-Panels für Zuhause: Wellenlängen & Preise",
                            "Panneaux de Luminothérapie Rouge à Domicile : Guide 2026",
                            "Understand 660nm vs. 850nm irradiance ratings, FDA-cleared home devices, and realistic session schedules.",
                            "Was 660 nm und 850 nm wirklich bedeuten: Bestrahlungsstärke, Sicherheitszertifikate und aktuelle Gerätepreise.",
                            "Comprendre les longueurs d'onde 660 nm et 850 nm, l'irradiance réelle et les meilleurs panneaux domestiques.",
                            List.of("Compare Clinical-Grade Home Red Light Panels for 2026", "660nm vs 850nm Explained: Avoid Overpaying for Panels", "See Tested Irradiance & Recovery Results Before You Buy"),
                            List.of("Rotlicht-Panels 2026: Echte Leistung statt Marketing", "660 nm & 850 nm Heimgeräte im objektiven Vergleich", "Preise, Zertifizierungen und Erfahrungen im Überblick"),
                            List.of("Panneaux Lumière Rouge 2026 : Comparez les Modèles", "660 nm vs 850 nm : Comment Bien Choisir Son Appareil", "Guide Pratique et Meilleurs Rapports Qualité-Prix")),
                    sub("RowingMachine", "magnetic rowing machine quiet home",
                            "Quiet Magnetic Rowing Machines for Home Cardio (2026)",
                            "Leise Magnet-Rudergeräte für das Wohnzimmer im Vergleich",
                            "Rameurs Magnétiques Silencieux pour la Maison (2026)",
                            "Low-impact full-body cardio without disturbing neighbors: water vs. magnetic resistance compared by price and warranty.",
                            "Gelenkschonendes Ganzkörpertraining im Wohnzimmer: Wasser- vs. Magnetwiderstand, Geräuschpegel und Garantie.",
                            "Cardio complet sans impact et silencieux : comparaison résistance magnétique vs eau, pliage et garantie.",
                            List.of("Full-Body Cardio in 20 Minutes — Quiet Home Rowers", "Magnetic vs Water Rowing Machines: 2026 Cost Breakdown", "Fold-Up Rowing Machines Rated for Small Homes"),
                            List.of("Flüsterleise Rudergeräte für Mietwohnungen 2026", "Magnet- vs. Wasserrudergerät: Was passt besser?", "Klappbare Rudergeräte im unabhängigen Vergleich"),
                            List.of("Rameurs Silencieux Pliables : Le Comparatif 2026", "Résistance Magnétique ou Eau ? Prix et Avis Complets", "Entraînement Complet à Domicile Sans Bruit"))
            )),
            new VerticalSpec(1, "smart-security", "Smart Home & Security", "Taboola", 1.15, List.of(
                    sub("NoFeeSecurityCam", "home security cameras no monthly fee",
                            "Top 5 Home Security Cameras With Zero Monthly Subscription",
                            "Überwachungskameras ohne Abo 2026: Lokaler Speicher im Test",
                            "Caméras de Surveillance Sans Abonnement 2026 : Top 5",
                            "Protect your porch and driveway with local-storage 4K cameras that skip recurring cloud fees altogether.",
                            "Schützen Sie Haus und Hof mit 4K-Kameras und lokalem Speicher ganz ohne laufende Cloud-Gebühren.",
                            "Sécurisez votre maison avec des caméras 4K à stockage local sans aucun forfait cloud mensuel.",
                            List.of("Homeowners Are Dropping Monthly Security Fees — Here's Why", "2026 Local-Storage Security Cameras Compared by Night Vision", "No-Contract Outdoor Cameras That Work Even Offline"),
                            List.of("Hausbesitzer sparen sich teure Cloud-Abos bei Kameras", "4K-Außenkameras mit lokalem Speicher im Vergleich 2026", "Nachtsicht, Solarbetrieb und Datenschutz im Check"),
                            List.of("Surveillez Votre Maison Sans Payer d'Abonnement Mensuel", "Top 5 des Caméras Extérieures 4K à Stockage Local", "Vision Nocturne Couleur et Autonomie Solaire Comparées")),
                    sub("SmartThermostat", "smart thermostat energy savings rebate",
                            "Smart Thermostats That Cut Heating Bills + Utility Rebates",
                            "Smarte Heizkörperthermostate 2026: Bis zu 22% Heizkosten sparen",
                            "Thermostats Connectés 2026 : Économies et Aides Financières",
                            "See which learning thermostats qualify for instant local utility rebates and actually pay for themselves in one winter.",
                            "Welche smarten Thermostate mit bestehenden Heizkörpern kompatibel sind und sich schon im ersten Winter amortisieren.",
                            "Quels thermostats intelligents sont éligibles aux primes énergie et réduisent réellement la facture de chauffage.",
                            List.of("Check Utility Rebates on 2026 Smart Thermostats Near You", "Cut Winter Heating Costs With Room-by-Room Control", "Compare Installation Ease & Energy Savings Reports"),
                            List.of("Heizkosten senken mit smarten Thermostaten — Vergleich 2026", "Kompatibilität, App-Steuerung und Sparpotenzial im Check", "Die besten Starter-Sets für Wohnung und Einfamilienhaus"),
                            List.of("Réduisez Votre Facture de Chauffage Avec un Thermostat Connecté", "Aides 2026 et Meilleurs Modèles Faciles à Installer", "Comparatif Compatibilité Chaudière et Radiateurs")),
                    sub("VideoDoorbell", "wireless video doorbell comparison",
                            "Best Wire-Free Video Doorbells for Renters & Homeowners",
                            "Kabellose Video-Türklingeln 2026: Akku & Datenschutz im Test",
                            "Sonnettes Vidéo Sans Fil 2026 : Guide et Comparatif",
                            "Battery life, head-to-toe package detection, and local chime options compared across 7 easy-install doorbells.",
                            "Akkulaufzeit, Paketerkennung und lokaler Speicher bei 7 kabellosen Video-Türklingeln im Praxistest.",
                            "Autonomie batterie, détection de colis et installation sans perçage : notre comparatif de 7 sonnettes vidéo.",
                            List.of("Install a Smart Video Doorbell in 5 Minutes — No Wiring", "Package Detection & Battery Life Compared for 2026", "See Which Video Doorbells Store Clips Locally for Free"),
                            List.of("Video-Türklingel ohne Kabel: 5-Minuten-Montage im Test", "Paketerkennung und Akkulaufzeit im direkten Vergleich", "DSGVO-konforme Klingelkameras ohne Cloud-Zwang"),
                            List.of("Installez une Sonnette Vidéo en 5 Minutes Sans Câblage", "Détection de Colis et Stockage Local : Le Top 2026", "Comparatif Spécial Locataires et Maisons Individuelles")),
                    sub("SmartDoorLock", "fingerprint smart door lock deadbolt",
                            "Keyless Fingerprint Smart Locks: Security Grades Explained",
                            "Smarte Türschlösser mit Fingerabdruck: Sicherheit im Vergleich",
                            "Serrures Connectées Biométriques 2026 : Sécurité et Prix",
                            "ANSI Grade 1 deadbolts, auto-lock timers, and temporary guest PINs reviewed for everyday reliability.",
                            "Sicherheitsklassen, Notstromversorgung und temporäre Gäste-Codes bei aktuellen Smart Locks im Überblick.",
                            "Niveaux de sécurité, codes temporaires et compatibilité cylindre européen : le guide complet des serrures connectées.",
                            List.of("Never Hide a Spare Key Again — Top 2026 Fingerprint Locks", "Compare ANSI Grade 1 Smart Deadbolts & Battery Backup", "See Easy Retrofit Locks That Keep Your Existing Keys"),
                            List.of("Schlüssellos ins Haus: Fingerabdruck-Türschlösser 2026", "Nachrüst-Smart-Locks für Mietwohnungen im Vergleich", "Sicherheitsstufen, Akkulaufzeit und App-Steuerung geprüft"),
                            List.of("Ouvrez Sans Clé : Top des Serrures Biométriques 2026", "Modèles Compatibles Porte Standard et Installation Facile", "Comparez Sécurité, Autonomie et Codes Invités")),
                    sub("WaterLeakSensor", "smart water leak detector shutoff",
                            "Smart Water Leak Detectors That Prevent Costly Home Damage",
                            "Smarte Wassermelder & Absperrventile: Schutz vor Wasserschäden",
                            "Détecteurs de Fuite d'Eau Connectés : Prévenir les Dégâts",
                            "Compare affordable under-sink leak alerts and automatic main-line shutoff valves that may qualify for insurance discounts.",
                            "Von günstigen Untertisch-Sensoren bis zum automatischen Hauptventil — so vermeiden Sie teure Leitungsschäden.",
                            "Capteurs sous évier et vannes d'arrêt automatiques : protégez votre logement et réduisez les risques de sinistre.",
                            List.of("A $35 Sensor That Could Save You $10,000 in Water Damage", "Smart Leak Detectors & Auto Shutoff Valves Compared", "See Which Home Insurers Discount Smart Leak Monitors"),
                            List.of("Wasserschäden früh erkennen: Smarte Sensoren ab 30 €", "Automatische Wasserstopp-Ventile im Vergleich 2026", "Welche Sensoren sofort per App und Sirene warnen"),
                            List.of("Évitez un Dégât des Eaux Coûteux Grâce aux Capteurs Connectés", "Détecteurs de Fuite et Vannes Automatiques Comparés", "Installation Simple Sous Évier et Alerte Instantanée"))
            )),
            new VerticalSpec(2, "travel-cards", "Travel Deals & Cards", "Google", 1.28, List.of(
                    sub("NoFeeTravelCard", "no annual fee travel credit card",
                            "Best No-Annual-Fee Travel Credit Cards With Zero FX Fees",
                            "Kostenlose Reisekreditkarten 2026 ohne Fremdwährungsgebühr",
                            "Meilleures Cartes Bancaires Voyage Sans Frais à l'Étranger",
                            "Earn flexible airline and hotel points without paying an annual fee—compare sign-up offers, FX waivers, and travel insurance.",
                            "Weltweit gebührenfrei bezahlen und Meilen sammeln: Aktuelle Reisekreditkarten ohne Jahresgebühr im Vergleich.",
                            "Payez sans frais de change partout dans le monde et cumulez des avantages voyage sans cotisation annuelle.",
                            List.of("Top 2026 Travel Cards With $0 Annual Fee & No Foreign Fees", "Compare Welcome Bonuses & Airline Transfer Partners", "See Instant Card Perks Before Your Next Trip"),
                            List.of("0 € Jahresgebühr & 0 % Fremdwährungsgebühr im Vergleich", "Reisekreditkarten 2026 mit inkludierter Reiseversicherung", "Aktuelle Konditionen für weltweites Bezahlen prüfen"),
                            List.of("Cartes Voyage 0 € de Frais à l'Étranger — Comparatif 2026", "Assurances Voyage Incluses et Plafonds de Retrait", "Trouvez la Carte Idéale Avant Votre Prochain Départ")),
                    sub("FlightDealsAlert", "cheap international flight deals 2026",
                            "How to Find 2026 International Flight Deals & Mistake Fares",
                            "Günstige Langstreckenflüge 2026: Preisalarme & Buchungstipps",
                            "Billets d'Avion Pas Chers 2026 : Comparateurs et Alertes",
                            "Compare flexible-date fare trackers, shoulder-season booking windows, and hidden-city rules before booking.",
                            "Flexible Reisedaten, optimale Buchungsfenster und seriöse Preisalarme für Europa-, USA- und Asienflüge.",
                            "Calendriers flexibles, meilleures fenêtres de réservation et alertes prix pour économiser sur vos vols.",
                            List.of("Compare Cheap Flights Across 400+ Airlines in One Search", "2026 Best Booking Windows for Europe & Asia Routes", "Track Price Drops Automatically Before Fares Jump"),
                            List.of("Flugpreise 2026 vergleichen: Bis zu 40 % auf Langstrecken sparen", "Die besten Buchungszeiten und Preisalarme im Überblick", "Flexible Flugsuche für Europa, USA und Asien"),
                            List.of("Comparez les Vols Pas Chers 2026 Sur Plus de 400 Compagnies", "Quand Réserver Pour Payer Moins Cher Votre Billet ?", "Activez les Alertes Baisse de Prix en Un Clic")),
                    sub("TravelInsurance", "annual multi trip travel insurance",
                            "Annual Multi-Trip Travel Insurance: Medical & Cancellation Guide",
                            "Jahres-Reiseversicherung 2026: Kranken- & Stornoschutz im Test",
                            "Assurance Voyage Annuelle Multi-Séjours : Comparatif 2026",
                            "Frequent flyers save up to 45% with annual policies—compare emergency medical caps, baggage delay, and pre-existing coverage.",
                            "Für Vielreisende oft deutlich günstiger: Auslandskrankenschutz, Reiserücktritt und Gepäckdeckung im Vergleich.",
                            "Voyageurs fréquents : comparez les plafonds médicaux, l'annulation toutes causes et les tarifs annuels.",
                            List.of("Compare Annual Travel Insurance Plans Starting Under $65", "Medical Evacuation & Trip Cancellation Caps Side by Side", "Check Coverage for Seniors & Family Trips in 2026"),
                            List.of("Jahres-Auslandskrankenversicherung ab 19 € vergleichen", "Reiserücktritt & Gepäckschutz 2026 im Leistungscheck", "Familien- und Seniorentarife übersichtlich gegenübergestellt"),
                            List.of("Assurance Voyage Annuelle : Comparez les Garanties 2026", "Frais Médicaux à l'Étranger et Annulation au Meilleur Prix", "Devis Rapide Pour Séjours en Europe et Hors UE")),
                    sub("AirportLounge", "airport lounge access pass compare",
                            "Cheapest Ways to Get Unlimited Airport Lounge Access in 2026",
                            "Flughafen-Lounge Zugang 2026: Pässe & Karten im Vergleich",
                            "Accès Salons d'Aéroport 2026 : Cartes et Pass Comparés",
                            "Compare Priority Pass tiers, guest fee rules, and airline club memberships to see which pass fits your flight frequency.",
                            "Priority Pass, DragonPass und Premium-Karten im Preisvergleich — ab wie vielen Flügen sich der Lounge-Pass rechnet.",
                            "Priority Pass, cartes premium et accès invités : calculez l'option la plus rentable selon vos vols annuels.",
                            List.of("Skip Crowded Gates: Affordable Airport Lounge Passes 2026", "Compare Priority Pass Guest Policies & Free Dining Credits", "See Which Travel Cards Include Complimentary Lounges"),
                            List.of("Entspannt vor dem Abflug: Lounge-Zugänge im Preisvergleich", "Priority Pass & Reisekarten mit kostenlosem Lounge-Eintritt", "Lohnt sich ein Lounge-Pass bei 4 Flügen im Jahr?"),
                            List.of("Accédez Aux Salons d'Aéroport Sans Payer le Tarif Plein", "Comparatif Priority Pass et Cartes Bancaires Incluses", "Confort, Wi-Fi et Restauration Avant Embarquement")),
                    sub("TravelEsim", "international travel esim unlimited data",
                            "Best International Travel eSIM Plans: Coverage & Speed Test",
                            "Reise-eSIM 2026 im Vergleich: Datenpakete für USA, Japan & EU",
                            "Meilleures eSIM de Voyage 2026 : Prix et Débits Comparés",
                            "Avoid $12/day carrier roaming fees with instant QR eSIMs—compare 5G partner networks and hotspot allowances.",
                            "Hohe Roaming-Gebühren vermeiden: Aktuelle Reise-eSIM-Anbieter im Vergleich nach Preis pro GB und 5G-Netzabdeckung.",
                            "Évitez les frais d'itinérance hors forfait : comparatif des eSIM internationales par pays, débit 5G et partage de connexion.",
                            List.of("Skip $12/Day Roaming — Compare 2026 Global Travel eSIMs", "Instant 5G Data in 190+ Countries From $4.50", "Side-by-Side Speed & Hotspot Comparison for Travelers"),
                            List.of("Teures Roaming sparen: Reise-eSIMs ab 4,50 € im Vergleich", "Sofort online in USA, Asien und Schweiz mit 5G-eSIM", "Datenvolumen, Hotspot-Funktion und Netzqualität geprüft"),
                            List.of("Fini le Hors-Forfait : Comparez les eSIM Voyage 2026", "Internet 5G Instantané dans 190+ Pays Dès 4,50 €", "Débit Réel, Partage de Connexion et Prix au Go"))
            )),
            new VerticalSpec(3, "meal-delivery", "Meal Kits & Nutrition", "Facebook", 1.08, List.of(
                    sub("HighProteinMeals", "high protein prepared meal delivery",
                            "Best High-Protein Prepared Meal Delivery Services (2026)",
                            "High-Protein Fertiggerichte im Abo: Geschmack & Preis 2026",
                            "Livraison de Repas Riches en Protéines : Comparatif 2026",
                            "We compared cost per serving, macros, sodium levels, and freshness across 8 heat-and-eat high-protein meal plans.",
                            "Preis pro Portion, Eiweißgehalt und Frische von 8 beliebten Fitness-Essenslieferdiensten im direkten Vergleich.",
                            "Prix par plat, apports en protéines et qualité des ingrédients : 8 services de repas préparés testés.",
                            List.of("40g+ Protein Meals Ready in 2 Minutes — Compare Top Plans", "2026 Prepared Meal Delivery Rated on Taste & Price/Serving", "See Fresh Never-Frozen Plans With Introductory Discounts"),
                            List.of("Proteinreiche Frischegerichte in 3 Minuten — Vergleich 2026", "Nährwerte, Portionspreise und Abo-Flexibilität im Check", "Die beliebtesten Fitness-Lieferboxen ohne Kochstress"),
                            List.of("Repas Riches en Protéines Prêts en 2 Min : Le Top 2026", "Comparez Prix par Plat, Macros et Flexibilité d'Abonnement", "Plats Frais Équilibrés Livrés Chez Vous Chaque Semaine")),
                    sub("SeniorMealDelivery", "senior home delivered meals affordable",
                            "Affordable Home-Delivered Meals for Seniors: 2026 Guide",
                            "Essen auf Rädern & Senioren-Menüdienste im Preisvergleich",
                            "Portage de Repas à Domicile Pour Seniors : Tarifs 2026",
                            "Low-sodium, heart-healthy, and diabetic-friendly delivered meals compared by price, portion size, and flexible pause rules.",
                            "Salzarme, herzgesunde und diabetikerfreundliche Menüdienste im Vergleich nach Preis, Portionsgröße und Liefergebiet.",
                            "Menus équilibrés sans sel ajouté ou adaptés diabète : comparez tarifs, aides fiscales et flexibilité de livraison.",
                            List.of("Nutritious Senior Meals Delivered Fresh — Compare Weekly Costs", "Low-Sodium & Heart-Healthy Menu Options Reviewed for 2026", "Easy-Open Trays With Flexible Skip-Any-Week Delivery"),
                            List.of("Gesunde Senioren-Menüs direkt nach Hause — Preise 2026", "Herzgesunde & salzarme Wochenpläne im Vergleich", "Ohne Vertragsbindung: Menüdienste nach Region prüfen"),
                            List.of("Repas Équilibrés Livrés aux Seniors : Comparez les Tarifs", "Menus Faibles en Sel et Crédit d'Impôt Expliqués", "Livraison Flexible Sans Engagement de Durée")),
                    sub("DiabeticMealPlan", "diabetic friendly meal delivery service",
                            "Best Low-Carb & Diabetic-Friendly Meal Kits Compared",
                            "Low-Carb & Diabetiker-Kochboxen 2026 im Überblick",
                            "Box Repas Indice Glycémique Bas : Comparatif 2026",
                            "Dietitian-designed menus under 35g net carbs per meal—compare glycemic balance, grocery savings, and customer ratings.",
                            "Von Ernährungsberatern geprüfte Low-Carb-Menüs: Kohlenhydrate, Zutatenqualität und Wochenkosten im Check.",
                            "Menus conçus par des diététiciens à faible charge glycémique : qualité des produits et prix par repas.",
                            List.of("Dietitian-Approved Low-Carb Meals — Compare 2026 Services", "Under 35g Net Carbs Without Spending Hours Prepping", "See Menu Variety & Cost Per Serving Side by Side"),
                            List.of("Low-Carb-Gerichte ohne Kalorienzählen — Vergleich 2026", "Ausgewogene Menüs mit niedrigem glykämischen Index", "Wochenkosten und Zutatenqualität transparent verglichen"),
                            List.of("Repas IG Bas Livrés à Domicile : Le Guide Complet 2026", "Menus Validés par des Nutritionnistes — Comparez les Prix", "Gagnez du Temps Tout en Maîtrisant Vos Glucides")),
                    sub("FamilyMealKit", "budget family meal kit under 8 dollars",
                            "Most Affordable Family Meal Kits Under $8 Per Serving",
                            "Günstige Familien-Kochboxen 2026: Preis pro Portion im Test",
                            "Box à Cuisiner Famille Pas Chères : Comparatif 2026",
                            "Cut weeknight grocery bills and food waste with kid-friendly 25-minute meal kits compared by real total checkout price.",
                            "Weniger Lebensmittelverschwendung und schnelle 25-Minuten-Rezepte: Familien-Kochboxen nach echtem Gesamtpreis verglichen.",
                            "Réduisez le budget courses et le gaspillage : comparatif des box familiales rapides à moins de 5 € la portion.",
                            List.of("Family Dinners Under $8/Serving — 2026 Meal Kits Ranked", "25-Minute Kid-Friendly Recipes With Zero Food Waste", "Compare Real Weekly Costs Including Shipping Fees"),
                            List.of("Familien-Kochboxen im Preischeck: Ab 4,50 € pro Portion", "Schnelle 25-Minuten-Rezepte für den stressigen Alltag", "Echte Gesamtkosten inkl. Versand übersichtlich verglichen"),
                            List.of("Dîners en Famille Prêts en 25 Min : Comparez les Box 2026", "Prix Réel par Portion Frais de Port Inclus", "Moins de Gaspillage et Recettes Validées par les Enfants")),
                    sub("GlutenFreeMeals", "certified gluten free meal delivery",
                            "Certified Gluten-Free & Allergen-Safe Meal Delivery Guide",
                            "Glutenfreie & Allergiker-freundliche Lieferboxen 2026",
                            "Livraison de Repas Sans Gluten Certifiés : Comparatif 2026",
                            "Dedicated gluten-free kitchens, cross-contamination standards, and dairy-free swaps reviewed for 2026.",
                            "Welche Anbieter in getrennten Küchen produzieren und transparente Allergen-Filter für Zöliakie bieten.",
                            "Cuisines dédiées, prévention de la contamination croisée et options sans lactose analysées en détail.",
                            List.of("Dedicated Gluten-Free Kitchens — Top 2026 Meal Services", "Compare Allergen Safety & Weekly Menu Variety", "Fresh Gluten-Free Dinners Delivered Ready to Heat"),
                            List.of("Sicher glutenfrei essen: Geprüfte Menü-Lieferdienste 2026", "Allergen-Filter, Zutatenlisten und Preise im Überblick", "Frische Gerichte ohne Gluten und Laktose bequem geliefert"),
                            List.of("Repas Sans Gluten Certifiés : Les Meilleurs Services 2026", "Sécurité Allergènes et Cuisines Dédiées Comparées", "Menus Variés Livrés Frais Chaque Semaine"))
            )),
            new VerticalSpec(4, "ergo-office", "Ergonomic Desk & Office", "TikTok", 1.06, List.of(
                    sub("StandingDesk", "dual motor standing desk comparison",
                            "Best Dual-Motor Standing Desks for Wobble-Free Work (2026)",
                            "Elektrische Schreibtische mit Doppelmotor im Test 2026",
                            "Bureaux Assis-Debout Double Moteur : Comparatif 2026",
                            "We tested stability at full standing height, lift speed, warranty length, and real desktop thickness across 7 brands.",
                            "Stabilität in maximaler Höhe, Hubgeschwindigkeit, Garantie und Tischplattenqualität von 7 Modellen im Vergleich.",
                            "Stabilité à hauteur maximale, silence des moteurs et garantie : 7 bureaux assis-debout passés au crible.",
                            List.of("Stop Back Pain at Work — Best Dual-Motor Standing Desks", "Zero-Wobble Electric Desks Compared Under $599", "See 10-Year Warranty Picks & Frame Stability Tests"),
                            List.of("Höhenverstellbare Schreibtische 2026: Stabilität im Test", "Doppelmotor-Gestelle mit 10 Jahren Garantie verglichen", "Ergonomisch arbeiten im Homeoffice ohne Wackeln"),
                            List.of("Bureaux Assis-Debout Stables : Le Top 7 de 2026", "Double Moteur Silencieux et Garantie Longue Durée", "Comparez Prix, Charge Maximale et Finitions")),
                    sub("ErgonomicChair", "ergonomic office chair lumbar support",
                            "Best Ergonomic Office Chairs for 8+ Hour Workdays (2026)",
                            "Ergonomische Bürostühle für Rückenschmerzen im Vergleich 2026",
                            "Chaises de Bureau Ergonomiques 2026 : Soutien Lombaire",
                            "Dynamic lumbar support, breathable mesh vs. foam cushions, and sub-$500 alternatives to $1,500 flagship chairs.",
                            "Dynamische Lordosenstütze, Netzrücken vs. Polster und starke Alternativen unter 500 € im Praxistest.",
                            "Soutien lombaire dynamique, maille respirante et meilleures alternatives à moins de 500 € testées sur 8 h/jour.",
                            List.of("Working 8+ Hours? Compare Top Lumbar-Support Office Chairs", "Best Sub-$500 Ergonomic Chairs That Rival $1,400 Brands", "Breathable Mesh & 4D Armrests Rated for All-Day Comfort"),
                            List.of("Rückenschonend sitzen: Top-Bürostühle unter 500 € im Test", "Lordosenstütze, Synchronmechanik und Garantie verglichen", "Die besten Homeoffice-Stühle für lange Arbeitstage"),
                            List.of("Mal de Dos au Bureau ? Comparez les Meilleures Chaises 2026", "Soutien Lombaire et Maille Respirante Sous 500 €", "Guide Complet Pour Travailler 8 h Sans Fatigue")),
                    sub("UltrawideMonitor", "ultrawide productivity monitor usb c",
                            "Best 34-Inch USB-C Ultrawide Monitors for Home Office",
                            "34-Zoll USB-C Ultrawide-Monitore fürs Homeoffice 2026",
                            "Écrans Ultrawide 34 Pouces USB-C : Comparatif Bureau",
                            "One-cable laptop charging, text sharpness, and eye-care certifications compared for developers and analysts.",
                            "Ein-Kabel-Lösung mit 90W Power Delivery, Textschärfe und Augenschutz-Zertifikate im direkten Vergleich.",
                            "Recharge PC par un seul câble USB-C, netteté du texte et confort visuel pour remplacer deux écrans.",
                            List.of("Replace Dual Monitors With One 34\" USB-C Ultrawide", "90W Laptop Charging & Crisp Text — Top 2026 Work Displays", "Compare IPS vs VA Panels for Productivity & Price"),
                            List.of("Ein 34-Zoll-Monitor statt Dual-Screen: USB-C Modelle 2026", "Textschärfe, 90W-Ladeleistung und Augenkomfort getestet", "Die besten Produktivitäts-Monitore im Preisvergleich"),
                            List.of("Remplacez Deux Écrans Par un 34\" USB-C Tout-en-Un", "Comparatif 2026 : Recharge 90W, Netteté et Confort Visuel", "Les Meilleurs Moniteurs Télétravail Par Budget")),
                    sub("DeskWalkingPad", "under desk walking pad quiet",
                            "Quiet Under-Desk Walking Pads: Hit 10,000 Steps While Working",
                            "Laufband unter dem Schreibtisch: Leise Walking-Pads 2026",
                            "Tapis de Marche Sous Bureau Silencieux : Guide 2026",
                            "Compare decibel ratings on video calls, motor longevity, and shock absorption across slim under-desk walkers.",
                            "Lautstärke in Videocalls, Dauerleistung und Gelenkschonung schlanker Under-Desk-Laufbänder im Vergleich.",
                            "Niveau sonore en visioconférence, moteur brushless et encombrement minimal : le comparatif complet.",
                            List.of("Hit 10,000 Steps on Zoom Calls — Quiet Under-Desk Pads", "Slim Walking Pads Compared for Noise & Durability", "See Top 2026 Desk Walkers Under $320"),
                            List.of("10.000 Schritte im Homeoffice: Flüsterleise Walking-Pads", "Schlanke Schreibtisch-Laufbänder unter 350 € im Test", "Geräuschpegel, Motorleistung und Tragkraft verglichen"),
                            List.of("Marchez en Travaillant : Tapis Sous Bureau Silencieux 2026", "Compatibles Visioconférence et Rangement Ultra-Plat", "Comparez Prix, Moteur et Amorti")),
                    sub("NoiseCancelHeadset", "noise cancelling headset work calls",
                            "Best Noise-Cancelling Headsets for Clear Work Calls (2026)",
                            "Noise-Cancelling Headsets für klare Videocalls im Test",
                            "Casques Réduction de Bruit Pour Télétravail : Top 2026",
                            "AI mic isolation, all-day clamping comfort, and multipoint Bluetooth switching tested in noisy cafes and homes.",
                            "KI-Mikrofonfilter, Tragekomfort und Multipoint-Bluetooth im Härtetest bei Hintergrundgeräuschen.",
                            "Isolation vocale IA, confort longue durée et connexion multipoint testés en open space et à la maison.",
                            List.of("Crystal-Clear Voice Calls Even in Noisy Rooms — 2026 Picks", "Compare ANC Headsets for Battery Life & Mic Clarity", "All-Day Comfort Work Headphones Reviewed by Price"),
                            List.of("Klare Stimme trotz Baustelle: Top-Headsets für Videocalls", "ANC-Kopfhörer mit starkem Mikrofon im Vergleich 2026", "Akkulaufzeit, Multipoint und Tragekomfort geprüft"),
                            List.of("Appels Cristallins en Télétravail : Casques ANC 2026", "Micro Anti-Bruit IA et Autonomie 40 h Comparés", "Trouvez le Casque Idéal Pour Vos Réunions"))
            )),
            new VerticalSpec(5, "solar-power", "Outdoor & Backup Power", "Taboola", 1.18, List.of(
                    sub("SolarGenerator", "portable solar generator home backup",
                            "Best Portable Solar Generators for Home Power Outages (2026)",
                            "Tragbare Solar-Powerstations für Notstrom & Camping 2026",
                            "Générateurs Solaires Portables Pour Maison & Camping 2026",
                            "LiFePO4 battery longevity, fridge runtime hours, and fast solar recharge speeds compared across top 1kWh–3kWh units.",
                            "LiFePO4-Zellen, Kühlschrank-Laufzeit und schnelle Solarladung bei 1–3 kWh Powerstations im direkten Vergleich.",
                            "Batteries LiFePO4 3000 cycles, autonomie réfrigérateur et recharge solaire rapide : comparatif 1 à 3 kWh.",
                            List.of("Keep Your Fridge & Wi-Fi Running in Any Blackout — 2026 Guide", "LiFePO4 Solar Generators Compared by Watt-Hour Value", "Silent Indoor-Safe Home Backup Without Gas Fumes"),
                            List.of("Sicher durch den Stromausfall: Solar-Powerstations 2026", "LiFePO4-Notstromspeicher nach Preis pro kWh verglichen", "Leise, abgasfrei und sofort einsatzbereit im Innenraum"),
                            List.of("Gardez Frigo et Box Internet Allumés en Cas de Coupure", "Comparatif Stations Électriques LiFePO4 + Panneaux Solaires", "Silencieuses, Sans Carburant et Prêtes en 1 Heure")),
                    sub("HomeBackupBattery", "whole home battery backup cost",
                            "Whole-Home Backup Batteries: 2026 Costs & Federal Tax Credits",
                            "Heimspeicher für Stromausfall: Kosten & Förderung 2026",
                            "Batteries de Secours Maison : Coûts et Aides 2026",
                            "Compare modular wall-mounted home batteries, automatic transfer switch costs, and 30% clean-energy credit eligibility.",
                            "Modulare Haus-Stromspeicher, automatische Notstromumschaltung und aktuelle Förderprogramme im Kostenvergleich.",
                            "Batteries murales modulaires, bascule automatique et rentabilité en autoconsommation : guide tarifaire 2026.",
                            List.of("Compare Whole-Home Battery Backup Costs & 2026 Incentives", "Keep HVAC & Essential Circuits Powered Automatically", "See Modular Battery Quotes Without Full Solar Rewiring"),
                            List.of("Haus-Stromspeicher 2026: Aktuelle Preise pro kWh prüfen", "Notstromfähige Speicher mit automatischer Umschaltung", "Förderungen und Amortisationszeit übersichtlich erklärt"),
                            List.of("Batteries Domestiques 2026 : Prix Réels et Rentabilité", "Alimentez Vos Circuits Essentiels Automatiquement", "Comparez les Solutions Modulaires Sans Tout Remplacer")),
                    sub("UltralightTent", "ultralight backpacking tent 2 person",
                            "Best Ultralight 2-Person Backpacking Tents Under 3 Pounds",
                            "Ultraleicht-Zelte für 2 Personen unter 1,5 kg im Test 2026",
                            "Tentes Ultralégères 2 Personnes Moins de 1,5 kg (2026)",
                            "Storm durability, condensation ventilation, and packed volume compared for weekend trails and thru-hikes.",
                            "Windstabilität, Belüftung gegen Kondenswasser und Packmaß im Praxistest für Trekking und Bikepacking.",
                            "Résistance au vent, ventilation anti-condensation et volume plié testés sur sentiers et bivouacs.",
                            List.of("Cut Pack Weight Fast — Top 2026 Ultralight 2P Tents", "Waterproof Ratings & Interior Space Compared Side by Side", "Best Budget-Friendly Trekking Tents Under $300"),
                            List.of("Leicht unterwegs: 2-Personen-Trekkingzelte unter 1,5 kg", "Wassersäule, Aufbauzeit und Packmaß im Vergleich 2026", "Preis-Leistungs-Sieger fürs Bikepacking und Wandern"),
                            List.of("Allégez Votre Sac : Tentes 2 Places Sous 1,5 kg", "Imperméabilité, Espace Intérieur et Prix Comparés", "Les Meilleures Tentes Bivouac de 2026")),
                    sub("PortableFridge", "12v portable camping fridge freezer",
                            "Best 12V Portable Fridge-Freezers for Road Trips & Overlanding",
                            "12V Kompressor-Kühlboxen für Camper & Roadtrips 2026",
                            "Glacières à Compression 12V Pour Van & Camping (2026)",
                            "Ditch melted ice forever—compare dual-zone compressor efficiency, battery draw, and quiet night modes.",
                            "Schluss mit geschmolzenem Eis: Zwei-Zonen-Kompressorboxen im Vergleich nach Stromverbrauch und Kühlleistung.",
                            "Oubliez la glace fondue : comparatif des glacières 12V double zone par consommation batterie et silence.",
                            List.of("No More Soggy Coolers — Compare 12V Compressor Fridges", "Low Battery Draw & Dual-Zone Freezing Tested for 2026", "Top Portable Car Fridges for Camping & Tailgating"),
                            List.of("Nie wieder Eiswürfel kaufen: 12V-Kompressorboxen im Test", "Stromverbrauch an der Powerstation und Kühlleistung geprüft", "Zwei-Zonen-Kühlboxen für Vanlife und Camping 2026"),
                            List.of("Glacières 12V à Compression : Le Comparatif Vanlife 2026", "Faible Consommation Batterie et Double Zone Congélation", "Gardez Vos Aliments Frais Partout Sans Glace")),
                    sub("SolarPanelKit", "foldable 200w solar panel rv camping",
                            "Best Foldable 200W Portable Solar Panels: Efficiency Tested",
                            "Faltbare 200W-Solartaschen im Effizienz-Vergleich 2026",
                            "Panneaux Solaires Pliables 200W : Test de Rendement 2026",
                            "Real-world midday wattage output, IP68 waterproofing, and universal MC4 compatibility across 6 portable panels.",
                            "Reale Ladeleistung bei Sonne & Bewölkung, IP68-Wetterschutz und MC4-Kompatibilität von 6 Solartaschen geprüft.",
                            "Puissance réelle mesurée, étanchéité IP68 et compatibilité universelle MC4 sur 6 panneaux pliables.",
                            List.of("Real Midday Wattage Tested — Top 200W Portable Solar Panels", "IP68 Weatherproof Folding Panels for RVs & Home Backup", "Compare Conversion Efficiency & Price Per Watt"),
                            List.of("200W-Solartaschen im Praxistest: Echte Watt-Ausbeute 2026", "Wetterfeste ETFE-Panels mit MC4-Anschluss verglichen", "Wirkungsgrad, Gewicht und Preis pro Watt im Überblick"),
                            List.of("Panneaux Solaires Pliables 200W : Rendement Réel Testé", "Étanches IP68 et Compatibles Toutes Stations Portables", "Comparez Poids, Efficacité et Prix au Watt"))
            )),
            new VerticalSpec(6, "home-storage", "Home Storage & Remodel", "Google", 1.12, List.of(
                    sub("GarageStorage", "heavy duty garage wall storage system",
                            "Best Heavy-Duty Garage Wall & Overhead Storage Systems",
                            "Schwerlast-Garagenregale & Wandsysteme im Vergleich 2026",
                            "Rangements Muraux et Plafond Pour Garage : Guide 2026",
                            "Reclaim two-car garage space with steel slatwall racks and ceiling hoists rated up to 600 lbs.",
                            "Mehr Platz in der Garage: Stabile Wand- und Deckensysteme mit bis zu 250 kg Traglast im Überblick.",
                            "Libérez le sol de votre garage avec des racks muraux et plateformes plafond supportant jusqu'à 250 kg.",
                            List.of("Transform a Cluttered Garage in One Weekend — Top Systems", "Compare 600-Lb Overhead Racks & Modular Wall Kits", "See Real Load Ratings & DIY Install Guides"),
                            List.of("Ordnung in der Garage an einem Wochenende — Top-Systeme", "Schwerlast-Wandregale & Deckenlifte im Traglast-Vergleich", "Einfache Selbstmontage und langlebige Stahlrahmen"),
                            List.of("Optimisez Votre Garage en Un Week-End : Solutions 2026", "Racks Plafond et Panneaux Muraux Modulaires Comparés", "Comparez Charge Maximale et Facilité de Pose")),
                    sub("KitchenOrganizer", "pull out kitchen cabinet organizer",
                            "Pull-Out Kitchen Cabinet Organizers That Double Usable Space",
                            "Auszugssysteme für Küchenschränke: Mehr Stauraum ohne Umbau",
                            "Tiroirs Coulissants Pour Placards de Cuisine : Top 2026",
                            "Soft-close slide-out shelves, corner lazy Susans, and no-drill pantry racks compared by width and price.",
                            "Soft-Close-Teleskopschubladen und Ecklösungen zum einfachen Nachrüsten — Maße, Traglast und Preise.",
                            "Étagères coulissantes à fermeture douce et solutions d'angle sans perçage : comparez dimensions et prix.",
                            List.of("Stop Digging in Deep Cabinets — Slide-Out Pantry Shelves", "No-Drill Pull-Out Kitchen Organizers Compared for 2026", "Measure Once: Best Soft-Close Organizers by Cabinet Size"),
                            List.of("Nie wieder tief im Küchenschrank suchen — Nachrüst-Auszüge", "Teleskopschubladen mit Soft-Close einfach selbst montieren", "Maße, Tragkraft und Preise im schnellen Vergleich"),
                            List.of("Doublez l'Espace de Vos Placards Avec des Tiroirs Coulissants", "Modèles Sans Perçage et Fermeture Douce Comparés", "Trouvez la Largeur Exacte Pour Votre Cuisine")),
                    sub("WalkInCloset", "modular custom closet system affordable",
                            "Affordable Modular Closet Systems vs. Custom Installers",
                            "Modulare Schranksysteme 2026: Preisvergleich nach Maß",
                            "Dressings Modulaires Sur Mesure : Comparatif Prix 2026",
                            "Compare DIY modular closet kits against professional custom builds—see real cost breakdowns for a 6-foot reach-in.",
                            "DIY-Schranksysteme gegen Maßanfertigung vom Schreiner: Echte Kostenbeispiele für 2 bis 3 Meter Breite.",
                            "Kits dressing modulaires vs sur-mesure installé : budget réel, solidité et délais pour un placard de 2 m.",
                            List.of("Custom-Look Closet Systems Without the $4,000 Price Tag", "Compare Modular Wire vs Wood Closet Kits for 2026", "Free Layout Ideas for Small Walk-In & Reach-In Closets"),
                            List.of("Ankleidezimmer nach Maß ohne Luxuspreis — Systeme 2026", "Modulare Kleiderschränke aus Holz & Metall im Vergleich", "Planungstipps für kleine Nischen und Dachschrägen"),
                            List.of("Un Dressing Sur Mesure Sans Payer 4 000 € — Comparatif", "Kits Bois vs Métal : Solidité et Prix au Mètre Linéaire", "Idées d'Aménagement Pour Petits Espaces")),
                    sub("BasementDehumidifier", "smart basement dehumidifier pump",
                            "Best High-Capacity Basement Dehumidifiers With Built-In Pump",
                            "Leistungsstarke Bautrockner & Keller-Luftentfeuchter 2026",
                            "Déshumidificateurs Sous-Sol Avec Pompe : Comparatif 2026",
                            "Stop damp odors and mold automatically: Energy Star 50-pint models compared by daily kWh cost and noise.",
                            "Feuchtigkeit und Schimmel im Keller dauerhaft stoppen: Energieeffiziente 25L–50L Geräte mit Pumpe im Test.",
                            "Stoppez l'humidité et les odeurs en sous-sol : modèles 25L à 50L avec pompe intégrée et faible conso.",
                            List.of("Keep Damp Basements Dry Automatically — Pump Models 2026", "Energy-Star 50-Pint Dehumidifiers Ranked on Electric Cost", "Continuous Drain & Smart Humidity Control Compared"),
                            List.of("Trockener Keller ohne Eimer leeren: Geräte mit Pumpe 2026", "Stromverbrauch, Entfeuchtungsleistung und Lautstärke geprüft", "Die besten Luftentfeuchter gegen Schimmelbildung"),
                            List.of("Assainissez Votre Sous-Sol Sans Vider le Bac — Pompe Intégrée", "Déshumidificateurs Basse Consommation Comparés 2026", "Capacité, Niveau Sonore et Contrôle Hygrométrique")),
                    sub("ModularSofaStorage", "storage sectional sofa small living room",
                            "Storage Sectional Sofas & Hidden-Space Living Room Picks",
                            "Schlafsofas mit Bettkasten für kleine Wohnzimmer 2026",
                            "Canapés d'Angle Avec Coffre de Rangement : Guide 2026",
                            "Washable performance fabrics, hydraulic lift storage chaises, and sleeper sofas reviewed for compact apartments.",
                            "Waschbare Bezüge, geräumige Bettkästen und stabile Federkerne für kompakte Wohnungen im Vergleich.",
                            "Tissus lavables, coffres à vérins et couchages quotidiens : notre sélection pour salons compacts.",
                            List.of("Hidden Storage + Washable Covers — Top 2026 Sectionals", "Compare Space-Saving Sleeper Sofas for Small Living Rooms", "Durable Pet-Friendly Fabrics & Delivery Lead Times"),
                            List.of("Sofas mit viel Stauraum & waschbarem Bezug — Top 2026", "Platzsparende Schlafsofas für kleine Apartments verglichen", "Komfort, Lieferzeiten und Preis-Leistung im Check"),
                            List.of("Canapés Coffre Déhoussables : Gagnez de la Place au Salon", "Modèles Convertibles Pour Petits Appartements Comparés", "Tissus Anti-Taches et Livraison Rapide"))
            )),
            new VerticalSpec(7, "online-degrees", "Online Courses & Degrees", "Facebook", 1.24, List.of(
                    sub("DataAnalyticsCert", "online data analytics certification accredited",
                            "Best Accredited Online Data Analytics Certifications (2026)",
                            "Berufsbegleitende Data-Analytics-Zertifikate 2026 im Vergleich",
                            "Certifications Data Analytics en Ligne Reconnues (2026)",
                            "Compare tuition cost, employer recognition, SQL/Python portfolio projects, and completion timelines for career switchers.",
                            "Studiengebühren, Arbeitgeber-Anerkennung, Python/SQL-Praxisprojekte und Fördermöglichkeiten im Überblick.",
                            "Tarifs, financement CPF, projets SQL/Python et insertion professionnelle : comparatif des formations certifiantes.",
                            List.of("Launch a Data Analytics Career Online in 4–6 Months", "Compare Tuition, Employer Recognition & Job Placement", "See Accredited Programs With Flexible Evening Schedules"),
                            List.of("Data Analyst berufsbegleitend lernen — Zertifikate 2026", "Kosten, Dauer und staatliche Förderung im Vergleich", "Praxisnahe SQL- & Python-Kurse für Quereinsteiger"),
                            List.of("Devenez Data Analyst à Distance : Formations Certifiantes 2026", "Comparez Prix, Financement CPF et Projets Pratiques", "Programmes Flexibles Compatibles Avec un Emploi")),
                    sub("CloudCertCourse", "aws azure cloud certification bootcamp",
                            "Cloud Engineer Certifications: AWS vs. Azure Career ROI Guide",
                            "Cloud-Zertifizierungen 2026: AWS vs. Azure Gehalt & Kurse",
                            "Certifications Cloud AWS vs Azure : Comparatif Carrière 2026",
                            "Exam pass rates, hands-on lab sandboxes, and real salary lifts across AWS Solutions Architect and Azure tracks.",
                            "Prüfungskosten, Praxis-Labs und Gehaltsentwicklung bei AWS Solutions Architect und Microsoft Azure.",
                            "Taux de réussite, labs pratiques et évolution salariale pour les certifications AWS et Azure.",
                            List.of("AWS vs Azure Certification: Which Pays More in 2026?", "Compare Self-Paced Cloud Bootcamps With Real Lab Access", "See Exam Vouchers & Beginner-to-Architect Roadmaps"),
                            List.of("AWS oder Azure? Cloud-Zertifikate mit bestem Karriere-ROI", "Praxisnahe Online-Kurse inkl. Lab-Umgebung verglichen", "Prüfungsvorbereitung und Kosten im Überblick 2026"),
                            List.of("AWS vs Azure en 2026 : Quelle Certification Choisir ?", "Formations en Ligne Avec Labs Pratiques et Examens Blancs", "Comparez Coût, Durée et Débouchés Salariaux")),
                    sub("CybersecurityBootcamp", "online cybersecurity bootcamp part time",
                            "Part-Time Online Cybersecurity Programs: Tuition & Outcomes",
                            "IT-Sicherheit & Cybersecurity Weiterbildung Online 2026",
                            "Formations Cybersécurité en Ligne : Comparatif 2026",
                            "CompTIA Security+ prep, SOC analyst simulations, and scholarship options compared for working adults.",
                            "CompTIA Security+, SOC-Praxissimulationen und anerkannte Weiterbildungsabschlüsse im direkten Vergleich.",
                            "Préparation CompTIA Security+, ateliers SOC et financements disponibles pour adultes en reconversion.",
                            List.of("Break Into Cybersecurity While Keeping Your Day Job", "Compare Part-Time Programs With CompTIA Exam Prep", "See Tuition Aid & Verified Graduate Hiring Rates"),
                            List.of("Quereinstieg Cybersecurity: Berufsbegleitende Kurse 2026", "Praxisnahe SOC-Labs und Zertifikate im Vergleich", "Fördermöglichkeiten und Kosten transparent prüfen"),
                            List.of("Reconversion Cybersécurité à Distance : Le Guide 2026", "Ateliers Pratiques SOC et Préparation Certifications", "Comparez Programmes, Durées et Aides au Financement")),
                    sub("OnlineMbaAffordable", "accredited affordable online mba programs",
                            "Most Affordable Accredited Online MBA Programs Under $15K",
                            "Anerkannte Online-MBA-Studiengänge unter 12.000 € (2026)",
                            "MBA en Ligne Accrédités et Accessibles : Comparatif 2026",
                            "AACSB-accredited flexible MBA tracks with no GMAT required—compare total tuition, specializations, and pacing.",
                            "Akkreditierte Fern-MBA-Programme ohne Präsenzpflicht: Gesamtkosten, Schwerpunkte und Flexibilität im Check.",
                            "Programmes MBA à distance accrédités et flexibles : frais de scolarité réels, spécialisations et rythme.",
                            List.of("AACSB-Accredited Online MBAs Under $15,000 Total Tuition", "No-GMAT Flexible Degree Tracks for Working Managers", "Compare Total Cost, Graduation Time & Specializations"),
                            List.of("Berufsbegleitender Online-MBA 2026: Akkreditierte Hochschulen", "Gesamtkosten, Schwerpunkte und Studiendauer verglichen", "Flexibel studieren ohne Karrierepause"),
                            List.of("MBA en Ligne Accrédités : Comparez les Tarifs 2026", "Formations Flexibles Pour Cadres en Activité", "Spécialisations Finance, Tech et Management Comparées")),
                    sub("AiPromptEngineering", "applied ai engineering course business",
                            "Practical Applied AI & Automation Courses for Professionals",
                            "Praxisnahe KI- & Automatisierungskurse für Berufstätige 2026",
                            "Formations IA Appliquée et Automatisation : Top 2026",
                            "Move beyond basic prompts—learn LLM workflows, RAG pipelines, and business automation with verified certificates.",
                            "Mehr als Basics: KI-Workflows, Unternehmens-Automatisierung und geprüfte Zertifikate im Praxistest.",
                            "Au-delà des prompts basiques : workflows IA, automatisation métier et certifications reconnues.",
                            List.of("Master Applied AI Workflows for Your Career in 8 Weeks", "Compare Hands-On AI Automation Courses & Certificates", "No-Code to Python Tracks Ranked by Real Project Depth"),
                            List.of("KI-Automatisierung im Beruf anwenden — Top-Kurse 2026", "Von No-Code bis Python: Praxisprojekte & Kosten geprüft", "Anerkannte Weiterbildungen für Fach- und Führungskräfte"),
                            List.of("Maîtrisez les Workflows IA Appliqués au Métier en 8 Semaines", "Formations Pratiques No-Code et Python Comparées", "Certifications Professionnelles et Financements 2026"))
            )),
            new VerticalSpec(8, "personal-finance", "Personal Finance & Savings", "TikTok", 1.34, List.of(
                    sub("HighYieldSavings", "high yield savings account rates 2026",
                            "Highest FDIC-Insured High-Yield Savings Rates (Nov 2026)",
                            "Tagesgeld-Vergleich November 2026: Top-Zinsen mit Einlagensicherung",
                            "Meilleurs Livrets d'Épargne à Haut Rendement (Nov 2026)",
                            "Compare APY tiers, zero monthly maintenance fees, and instant transfer limits across insured online savings accounts.",
                            "Aktuelle Tagesgeldzinsen, Zinsgarantien und gesetzliche Einlagensicherung für Neu- und Bestandskunden im Vergleich.",
                            "Comparez les taux boostés, la fiscalité et la disponibilité immédiate des meilleurs livrets bancaires sécurisés.",
                            List.of("Stop Earning 0.05% at Big Banks — Compare 2026 HYSA Rates", "FDIC-Insured Online Savings With $0 Fees & No Minimums", "See Updated November 2026 APY Offers Side by Side"),
                            List.of("Mehr Zinsen fürs Ersparte: Top-Tagesgeldkonten Nov 2026", "100 % Einlagensicherung & kostenlose Kontoführung prüfen", "Aktuelle Aktionszinsen und Laufzeiten direkt vergleichen"),
                            List.of("Comparez les Meilleurs Taux d'Épargne Sécurisée (Nov 2026)", "Zéro Frais de Gestion et Retrait Disponible à Tout Moment", "Taux Boostés et Plafonds Mis à Jour Cette Semaine")),
                    sub("DebtConsolidation", "debt consolidation loan low apr compare",
                            "Debt Consolidation Loans: Compare Low-APR Fixed Rates (2026)",
                            "Umschuldungskredit 2026: Zinsen senken & Raten bündeln",
                            "Rachat de Crédits 2026 : Comparez les Taux Fixes",
                            "Combine high-APR credit cards into one predictable monthly payment—compare soft-pull prequalification and origination fees.",
                            "Teure Dispo- und Kreditkartenzinsen ablösen: Aktuelle Umschuldungsangebote schufaneutral vergleichen.",
                            "Regroupez vos mensualités en un taux fixe plus bas : comparez TAEG, frais de dossier et durée.",
                            List.of("Lower High Credit Card APRs Into One Fixed Monthly Payment", "Check Personal Consolidation Rates Without Hurting Credit", "Compare Zero-Origination-Fee Lenders for 2026"),
                            List.of("Kredite bündeln und Zinsen sparen — Umschuldung 2026", "Konditionen unverbindlich prüfen ohne Schufa-Nachteil", "Feste Monatsraten und Sondertilgung im Vergleich"),
                            List.of("Allégez Vos Mensualités Avec un Rachat de Crédits à Taux Fixe", "Simulation Gratuite et Comparatif des TAEG 2026", "Zéro Frais Cachés : Comparez les Organismes Agréés")),
                    sub("CdRatesCompare", "best 12 month cd rates fdic insured",
                            "Best 6-Month & 12-Month CD Rates: Lock In Fixed Yields",
                            "Festgeld 6 & 12 Monate 2026: Sichere Zinsen festschreiben",
                            "Comptes à Terme 6 et 12 Mois : Comparez les Rendements",
                            "Lock in guaranteed fixed yields before rate shifts—compare minimum deposits, early withdrawal rules, and compounding.",
                            "Planbare Rendite ohne Börsenrisiko: 6- und 12-Monats-Festgelder mit EU-Einlagensicherung im Zinsvergleich.",
                            "Bloquez un rendement garanti sur 6 ou 12 mois : seuils d'entrée, fiscalité et garanties bancaires comparés.",
                            List.of("Lock In Fixed 12-Month CD Yields Before Rates Move", "Compare Insured 6-Month & 1-Year CDs With Low Minimums", "No-Penalty CDs Explained: Flexibility Plus High Yield"),
                            List.of("Sichere Festgeldzinsen für 6–12 Monate im Vergleich 2026", "Top-Banken mit starker Bonität und einfacher Eröffnung", "Zinsgarantie ohne Kursrisiko — jetzt Konditionen prüfen"),
                            List.of("Bloquez un Taux Garanti Sur 6 ou 12 Mois — Comparatif 2026", "Comptes à Terme Sécurisés Dès 1 000 € de Dépôt", "Comparez Rendement Net et Conditions de Sortie")),
                    sub("CreditBuilderCard", "secured credit builder card no annual fee",
                            "Best Credit-Builder Cards With $0 Annual Fee & Fast Reporting",
                            "Kreditkarten mit einfacher Annahme & Score-Aufbau 2026",
                            "Cartes Sans Conditions de Revenus : Comparatif 2026",
                            "Build payment history across all three bureaus without hidden fees—compare deposit minimums and automatic graduation.",
                            "Transparente Prepaid- und Charge-Karten ohne versteckte Jahresgebühren für volle Kostenkontrolle im Vergleich.",
                            "Maîtrisez votre budget sans découvert avec des cartes gratuites à autorisation systématique comparées.",
                            List.of("Build Credit Faster With $0 Annual Fee Builder Cards", "Reports to All 3 Bureaus — Compare 2026 Starter Cards", "See Automatic Deposit Refund & Upgrade Timelines"),
                            List.of("Volle Kostenkontrolle: Kostenlose Karten ohne Gehaltsnachweis", "Transparente Gebühren und schnelle Online-Beantragung", "Die besten Einsteiger-Kreditkarten im Vergleich 2026"),
                            List.of("Cartes Bancaires Gratuites Sans Condition de Revenus 2026", "Zéro Risque de Découvert et Contrôle Budget Temps Réel", "Comparez Frais, Plafonds et Applications Mobiles")),
                    sub("BalanceTransfer", "0 percent apr balance transfer credit card",
                            "Longest 0% Intro APR Balance Transfer Cards (Up to 21 Months)",
                            "Ratenkauf & 0%-Finanzierungskarten 2026 im Gebührencheck",
                            "Cartes Paiement Fractionné Sans Frais : Guide 2026",
                            "Pause credit card interest for 18–21 months—compare transfer fees (3% vs. 5%) and payoff math before switching.",
                            "Zinsfreie Zahlungsziele bis zu 60 Tage und faire Teilzahlungsoptionen transparent verglichen.",
                            "Comparez les délais de paiement différé sans intérêts et les frais réels avant de changer de carte.",
                            List.of("Pay $0 Interest for Up to 21 Months on Transferred Balances", "Compare 3% vs 5% Transfer Fees & Real Interest Savings", "Calculate Your Debt-Free Payoff Timeline in 60 Seconds"),
                            List.of("Bis zu 60 Tage zinsfrei zahlen: Top-Karten im Vergleich", "Versteckte Teilzahlungszinsen vermeiden — der Check 2026", "Flexible Rückzahlung mit klaren Konditionen"),
                            List.of("Évitez les Intérêts Élevés : Comparez les Cartes à Débit Différé", "Frais Réels et Souplesse de Remboursement Analysés", "Trouvez la Solution la Plus Économique en 2026"))
            )),
            new VerticalSpec(9, "solar-rebates", "Solar & Home Incentives", "Taboola", 1.32, List.of(
                    sub("SolarRebates", "home solar panel rebates incentives 2026",
                            "2026 Home Solar Panel Rebates: State & Utility Incentives Guide",
                            "Photovoltaik-Förderung 2026: Zuschüsse & 0% MwSt im Überblick",
                            "Aides Panneaux Solaires 2026 : Prime Autoconsommation & Tarifs",
                            "Check zip-code eligibility for solar tax credits, net-metering updates, and realistic payback periods by roof size.",
                            "Alle Zuschüsse von Bund, Ländern und Kommunen sowie aktuelle Komplettpreise für 8–12 kWp Solaranlagen.",
                            "Montants de la prime à l'autoconsommation, TVA réduite et rentabilité réelle d'une installation 3 à 9 kWc.",
                            List.of("Homeowners: Check 2026 Solar Rebates Available in Your Zip Code", "See Real Solar Panel Payback Periods After Local Incentives", "Compare Zero-Down Leases vs Cash Purchase Savings"),
                            List.of("Hausbesitzer: Aktuelle PV-Förderungen & Preise 2026 prüfen", "Was eine 10-kWp-Solaranlage mit Speicher heute wirklich kostet", "0 % Mehrwertsteuer & regionale Zuschüsse im Überblick"),
                            List.of("Propriétaires : Vérifiez Vos Aides Panneaux Solaires 2026", "Prime Autoconsommation et Rentabilité par Région Expliquées", "Comparez les Devis 3 kWc, 6 kWc et 9 kWc")),
                    sub("HeatPumpRebate", "heat pump government rebate cost 2026",
                            "Heat Pump HVAC Rebates (2026): Up to $8,000 Off Installation",
                            "Wärmepumpe Förderung 2026: Bis zu 70 % Zuschuss im Vergleich",
                            "Aides Pompe à Chaleur 2026 : MaPrimeRénov' et Coûts Réels",
                            "Compare cold-climate heat pump efficiency, HEEHRA rebate income tiers, and real replacement quotes vs. gas furnaces.",
                            "KfW-Zuschüsse, Einkommensbonus und echte Installationskosten für Luft-Wasser-Wärmepumpen im Altbau.",
                            "Cumul MaPrimeRénov' + CEE, rendement par temps froid et devis moyens pour remplacer une chaudière gaz.",
                            List.of("Check Up to $8,000 in 2026 Heat Pump Replacement Rebates", "Cold-Climate Heat Pumps Compared on Winter Efficiency", "See Real HVAC Upgrade Costs Before Calling Contractors"),
                            List.of("Bis zu 70 % KfW-Zuschuss: Wärmepumpen-Förderung 2026 erklärt", "Was der Heizungstausch im Altbau nach Förderung kostet", "Luft-Wasser-Wärmepumpen nach Effizienz & Lautstärke verglichen"),
                            List.of("Pompe à Chaleur 2026 : Calculez Vos Aides MaPrimeRénov' + CEE", "Coût Réel Après Aides Pour Remplacer Votre Chaudière", "Comparez les Modèles Air-Eau Haute Performance")),
                    sub("WindowReplacement", "energy efficient window replacement cost",
                            "Energy-Efficient Window Replacement: 2026 Cost & Tax Credits",
                            "Fenster austauschen 2026: Kosten pro Fenster & BAFA-Zuschuss",
                            "Remplacement Fenêtres Double/Triple Vitrage : Prix & Aides",
                            "Double-pane vs. triple-pane glass, vinyl vs. fiberglass frames, and itemized per-window installation pricing.",
                            "Zweifach- vs. Dreifachverglasung, Kunststoff vs. Holz-Alu und 15–20 % Förderung für neue Fenster.",
                            "PVC, aluminium ou bois : prix posé par fenêtre, isolation thermique Uw et aides rénovation 2026.",
                            List.of("See 2026 Per-Window Replacement Prices Before Getting Quotes", "Cut Drafts & AC Bills With Energy-Star Triple-Pane Glass", "Check Qualifying Home Window Tax Credits Near You"),
                            List.of("Neue Fenster 2026: Echte Kosten pro Fenster inkl. Montage", "15–20 % staatlichen Zuschuss beim Fenstertausch nutzen", "Kunststoff, Holz oder Alu? Wärmeschutz im Vergleich"),
                            List.of("Prix Réel des Fenêtres Posées en 2026 : Le Guide Complet", "PVC, Alu ou Bois : Comparez Isolation et Budget", "Vérifiez Votre Éligibilité Aux Aides Rénovation")),
                    sub("RoofReplacement", "metal vs shingle roof replacement cost",
                            "Metal vs. Asphalt Shingle Roofs: 2026 Cost & Lifespan Guide",
                            "Dachsanierung 2026: Kosten pro m² & Dämmungs-Förderung",
                            "Rénovation Toiture 2026 : Prix au m² et Isolation Combinaison",
                            "Compare 50-year standing-seam metal roofs against architectural shingles, insurance discounts, and hail warranties.",
                            "Kosten pro Quadratmeter für Neueindeckung und Aufsparrendämmung plus Förderzuschüsse im Überblick.",
                            "Tuiles, ardoise ou bac acier : budget moyen au m², durée de vie et primes pour l'isolation sous toiture.",
                            List.of("Compare 2026 Roof Replacement Costs by Square Foot", "Metal vs Architectural Shingles: 30-Year Value Breakdown", "See Which Roof Upgrades Lower Home Insurance Rates"),
                            List.of("Dach neu decken 2026: Kosten pro m² realistisch kalkulieren", "Bis zu 20 % Förderung für Dachdämmung mitnehmen", "Tonziegel, Betondachstein & Metalldach im Vergleich"),
                            List.of("Refaire Sa Toiture en 2026 : Estimez le Prix au m²", "Combinez Rénovation et Isolation Pour Obtenir les Aides", "Comparez Tuiles, Ardoise et Zinc sur 40 Ans")),
                    sub("GutterGuards", "best gutter guard systems leaf protection",
                            "Best Gutter Guard Systems: DIY Micro-Mesh vs. Pro Install",
                            "Laubschutzgitter für Dachrinnen 2026: Systeme im Vergleich",
                            "Protège-Gouttières Anti-Feuilles : Comparatif et Prix 2026",
                            "Stop climbing ladders twice a year—compare stainless micro-mesh guards, ice-dam performance, and real per-foot pricing.",
                            "Nie wieder Dachrinnen säubern: Edelstahl-Feinmaschgitter und Stecksysteme im Preis- und Haltbarkeitstest.",
                            "Évitez les gouttières bouchées : grilles micro-mailles inox vs pose pro comparées au mètre linéaire.",
                            List.of("Never Clean Clogged Gutters Again — Compare 2026 Guards", "Micro-Mesh vs Foam vs Screen: Real Per-Foot Cost Guide", "See Which Gutter Guards Handle Pine Needles & Heavy Rain"),
                            List.of("Nie wieder verstopfte Dachrinnen: Laubschutz im Vergleich", "Edelstahl-Gitter vs. Bürstensysteme — was wirklich hält", "Kosten pro Meter und einfache Montage im Überblick"),
                            List.of("Fini les Gouttières Bouchées : Comparatif Anti-Feuilles 2026", "Grilles Inox Micro-Mailles vs Pose Professionnelle", "Comparez Durabilité et Prix au Mètre Linéaire"))
            )),
            new VerticalSpec(10, "auto-insurance", "Auto Insurance & EV", "Google", 1.38, List.of(
                    sub("SeniorAutoIns", "affordable auto insurance seniors over 55",
                            "Best Auto Insurance Rates for Drivers Over 55 (2026 Compare)",
                            "Günstige Kfz-Versicherung für Fahrer ab 55 im Vergleich 2026",
                            "Assurance Auto Conducteurs 55+ : Comparatif Tarifs 2026",
                            "Mature drivers often overpay by $420+/year—compare safe-driver discounts, low-mileage plans, and claim satisfaction.",
                            "Langjährige Fahrer zahlen oft zu viel im Alttarif: Senioren-Rabatte, Wenigfahrer-Tarife und Schadenservice verglichen.",
                            "Conducteurs expérimentés : comparez les remises bonus 50, formules petit rouleur et tarifs 2026.",
                            List.of("Drivers 55+ Could Save Hundreds on 2026 Auto Insurance", "Compare Low-Mileage & Safe-Driver Discounts in Minutes", "See Side-by-Side Quotes From Top-Rated Carriers"),
                            List.of("Kfz-Versicherung 2026 wechseln & bis zu 380 € sparen", "Wenigfahrer- und Sicherheitsrabatte für Fahrer 55+ prüfen", "Tarife mit Werkstattservice & Vollkasko direkt vergleichen"),
                            List.of("Conducteurs 55+ : Comparez Vos Tarifs Assurance Auto 2026", "Formules Petit Rouleur et Bonus 50 Au Meilleur Prix", "Devis Rapide Sans Engagement en 2 Minutes")),
                    sub("HomeEvCharger", "level 2 home ev charger installation cost",
                            "Best Level 2 Home EV Chargers & 2026 Installation Cost Guide",
                            "11 kW Wallbox fürs Zuhause 2026: Testsieger & Montagekosten",
                            "Bornes de Recharge Maison 7,4 kW : Prix, Crédit d'Impôt 2026",
                            "Compare 48A hardwired vs. NEMA 14-50 chargers, panel upgrade costs, and utility time-of-use charging rebates.",
                            "11 kW Wallboxen mit PV-Überschussladen, Installationskosten durch den Elektriker und Stromtarif-Tipps.",
                            "Bornes 7,4 kW pilotables, crédit d'impôt 500 € et coût réel d'installation par un électricien IRVE.",
                            List.of("Charge 8× Faster at Home — Top 2026 Level 2 EV Chargers", "Compare Hardwired vs Plug-In Units + Utility Rebates", "See Real Electrician Install Costs Before You Buy"),
                            List.of("Zuhause günstig laden: Top 11-kW-Wallboxen 2026 im Test", "PV-Überschussladen, App-Steuerung & echte Montagekosten", "Comparez Preise und Fördermöglichkeiten in Ihrer Region"),
                            List.of("Borne de Recharge Maison : Profitez du Crédit d'Impôt 2026", "Comparatif Bornes 7,4 kW Connectées et Tarif Pose IRVE", "Rechargez en Heures Creuses Jusqu'à 3× Moins Cher")),
                    sub("LowMileageAuto", "pay per mile car insurance compare",
                            "Pay-Per-Mile Auto Insurance: Worth It Under 8,000 Miles/Year?",
                            "Kilometer-Kfz-Versicherung 2026: Lohnt sich Pay-per-km?",
                            "Assurance Auto au Kilomètre 2026 : Économies Petits Rouleurs",
                            "Remote workers driving under 8,000 miles a year can cut premiums 30%—see base rates, per-mile fees, and privacy policies.",
                            "Wer unter 9.000 km im Jahr fährt, spart mit Kilometer- und Telematiktarifen spürbar Beitrag.",
                            "Moins de 8 000 km par an ? Comparez les formules au kilomètre et forfaits petit rouleur sans boîtier intrusif.",
                            List.of("Drive Less Than 8,000 Miles/Year? Pay Only for Miles Driven", "Compare Top Pay-Per-Mile Auto Insurance Plans for 2026", "See Exact Monthly Savings for Remote Workers"),
                            List.of("Wenigfahrer-Tarife 2026: Nur für gefahrene Kilometer zahlen", "Kfz-Beitrag im Homeoffice um bis zu 30 % senken", "Anbieter mit und ohne Telematik-Box im Vergleich"),
                            List.of("Vous Roulez Peu ? Payez Votre Assurance Auto au Kilomètre", "Jusqu'à 30 % d'Économies Sous 8 000 km/an — Comparatif", "Formules Forfait km et Sans Boîtier Analysées")),
                    sub("ExtendedCarWarranty", "extended auto warranty high mileage",
                            "Extended Auto Warranties for High-Mileage Cars: 2026 Review",
                            "Gebrauchtwagen-Garantieverlängerung 2026: Leistungen & Kosten",
                            "Extension de Garantie Panne Mécanique : Comparatif 2026",
                            "Powertrain vs. bumper-to-bumper coverage, claim payout speed, and direct mechanic payment compared transparently.",
                            "Motor-, Getriebe- und Elektronikschutz für Fahrzeuge über 100.000 km: Selbstbeteiligung und Ausschlüsse im Check.",
                            "Couverture moteur, boîte et électronique pour véhicules d'occasion : plafonds, franchises et délais de carence.",
                            List.of("Protect High-Mileage Vehicles From $4,000+ Repair Bills", "Compare Direct-Pay Extended Auto Warranties for 2026", "See Covered Parts, Deductibles & Real Claim Ratings"),
                            List.of("Schutz vor teuren Reparaturen ab 100.000 km — Vergleich 2026", "Reparaturkostenversicherung: Leistungen & Selbstbehalt prüfen", "Freie Werkstattwahl und schnelle Schadenabwicklung"),
                            List.of("Protégez Votre Voiture d'Occasion Contre les Pannes Coûteuses", "Garanties Panne Mécanique 2026 : Franchises et Plafonds", "Comparatif Indépendant Pour Véhicules +100 000 km")),
                    sub("EvInsuranceCompare", "electric vehicle auto insurance rates",
                            "Electric Vehicle Insurance Guide: Lower Your 2026 EV Premium",
                            "E-Auto-Versicherung 2026: Akku-Schutz & günstige Tarife",
                            "Assurance Voiture Électrique 2026 : Garanties Batterie & Prix",
                            "Battery pack protection, home charger coverage, and which insurers offer the lowest 2026 rates for Tesla & Rivian owners.",
                            "Allgefahrendeckung für den Hochvolt-Akku, Wallbox-Mitversicherung und spezielle E-Auto-Rabatte im Vergleich.",
                            "Protection batterie, câble de recharge et assistance panne sèche : comparez les meilleurs tarifs véhicules électriques.",
                            List.of("Why EV Insurance Rates Vary by 45% — Compare 2026 Carriers", "Check Battery & Home Charger Coverage Before Renewing", "Find Lower Rates for Hybrid & All-Electric Vehicles"),
                            List.of("E-Auto richtig versichern: Akku- & Wallbox-Schutz im Vergleich", "Spezielle Elektro-Tarife mit bis zu 20 % Öko-Rabatt 2026", "Pannenhilfe bei leerem Akku und Tierbiss-Folgeschäden prüfen"),
                            List.of("Assurance Voiture Électrique : Comparez les Tarifs 2026", "Garantie Batterie, Câble et Assistance 0 km Incluses", "Économisez Sur Votre Contrat Véhicule Électrique ou Hybride"))
            )),
            new VerticalSpec(11, "dental-care", "Dental & Vision Care", "Facebook", 1.36, List.of(
                    sub("DentalImplants", "affordable dental implants cost 2026",
                            "Affordable Dental Implants (2026): Full-Arch & Single Tooth Costs",
                            "Zahnimplantate Kosten 2026: Eigenanteil & Zusatzversicherung",
                            "Implants Dentaires 2026 : Prix Moyens et Remboursements",
                            "Compare single-tooth vs. All-on-4 pricing, dental school discounts, and 0% payment plans available in 2026.",
                            "Transparente Preisübersicht für Einzelimplantate, Festzuschuss der Krankenkasse und gute Zahnzusatz-Tarife.",
                            "Tarifs moyens d'un implant dentaire, prise en charge mutuelle et options de financement transparentes.",
                            List.of("See Real 2026 Dental Implant Costs Before Your Consultation", "Compare Single-Tooth & Full-Arch Financing Options", "Find Local Clinics Offering Transparent All-In Pricing"),
                            List.of("Zahnimplantate 2026: Echte Kosten & Zuschüsse im Überblick", "So senken Sie den Eigenanteil mit der richtigen Absicherung", "Preise für Einzelimplantat und Keramikkrone transparent erklärt"),
                            List.of("Prix d'un Implant Dentaire en 2026 : Guide et Remboursements", "Comparez les Devis et la Prise en Charge Mutuelle", "Solutions Fiables et Facilités de Paiement Expliquées")),
                    sub("ClearAligners", "invisible clear aligners price compare",
                            "Clear Teeth Aligners (2026): Orthodontist vs. Hybrid Cost Guide",
                            "Unsichtbare Zahnschienen 2026: Preise & Behandlung im Vergleich",
                            "Aligneurs Dentaires Transparents 2026 : Prix et Avis Comparatifs",
                            "Compare treatment length, in-person checkups, refinement guarantees, and monthly financing across leading clear aligners.",
                            "Behandlungsdauer, ärztliche Kontrolle und Gesamtkosten transparenter Aligner-Schienen für Erwachsene.",
                            "Durée de traitement, suivi orthodontiste et budget total : comparatif des gouttières transparentes adultes.",
                            List.of("Straighten Teeth Discreetly — Compare 2026 Clear Aligners", "Doctor-Monitored Aligner Costs & Monthly Payment Plans", "See Average Treatment Time & Insurance Coverage"),
                            List.of("Gerade Zähne ohne feste Spange: Aligner-Preise 2026", "Zahnärztlich begleitete Zahnschienen im Kostenvergleich", "Behandlungsdauer und Ratenzahlung transparent geprüft"),
                            List.of("Aligneurs Transparents Adultes : Comparez les Tarifs 2026", "Suivi Orthodontiste et Durée Moyenne de Traitement", "Remboursements Mutuelle et Paiement Mensuel Expliqués")),
                    sub("SeniorDentalPlan", "dental insurance plans seniors no waiting",
                            "Best Senior Dental Insurance Plans With No Waiting Period",
                            "Zahnzusatzversicherung ab 60 ohne Wartezeit: Vergleich 2026",
                            "Mutuelle Dentaire Senior Sans Délai de Carence (2026)",
                            "Preventive cleanings, crowns, and dentures covered immediately—compare annual maximums and monthly premiums.",
                            "Sofortschutz für Zahnersatz, Kronen und professionelle Zahnreinigung: Tarife ab 60 nach Leistung gestaffelt.",
                            "Prothèses, couronnes et 100 % Santé : comparez les mutuelles dentaires seniors sans délai d'attente.",
                            List.of("Dental Plans for Seniors With No Waiting Period — 2026 Guide", "Compare Coverage for Crowns, Bridges & Routine Cleanings", "See Monthly Premiums & In-Network Dentist Directories"),
                            List.of("Zahnzusatz für Senioren ohne Wartezeit — Testsieger 2026", "Bis zu 90 % Erstattung für Zahnersatz und Prophylaxe", "Beiträge und Leistungsstaffeln übersichtlich vergleichen"),
                            List.of("Mutuelle Dentaire Senior Sans Carence : Le Top 2026", "Remboursement Couronnes, Prothèses et Soins Courants", "Comparez les Garanties et Tarifs Mensuels en 1 Clic")),
                    sub("HearingAidsOtc", "rechargeable otc hearing aids review",
                            "Best Rechargeable OTC Hearing Aids (2026): Clarity & Comfort",
                            "Moderne wiederaufladbare Hörgeräte 2026: Preis & Kassenanteil",
                            "Appareils Auditifs Rechargeables 2026 : 100% Santé & Avis",
                            "Bluetooth streaming, background-noise suppression, and nearly invisible receiver-in-canal designs tested for speech clarity.",
                            "Bluetooth-Streaming, Sprachverständnis im Restaurant und fast unsichtbare Akku-Hörgeräte im Vergleich.",
                            "Bluetooth, réduction du bruit ambiant et modèles discrets rechargeables : guide complet 100 % Santé.",
                            List.of("Nearly Invisible Rechargeable Hearing Aids — 2026 Reviews", "Compare Speech Clarity in Noisy Rooms & 45-Day Trials", "FDA-Registered OTC Models Starting Under $499/Pair"),
                            List.of("Fast unsichtbare Akku-Hörgeräte im Vergleich 2026", "Klares Sprachverständnis im Restaurant & Bluetooth-Funktion", "Kassenzuschuss und Eigenanteil transparent erklärt"),
                            List.of("Aides Auditives Rechargeables Discrètes : Comparatif 2026", "Reste à Charge Zéro (100 % Santé) vs Haut de Gamme", "Autonomie, Bluetooth et Essai Gratuit de 30 Jours")),
                    sub("LasikVisionCost", "lasik eye surgery cost financing 2026",
                            "LASIK vs. SMILE Eye Surgery: 2026 Cost & Recovery Comparison",
                            "Augenlasern 2026: Femto-LASIK vs. ReLEx SMILE Kosten & Ablauf",
                            "Chirurgie Réfractive Laser 2026 : Prix LASIK vs SMILE",
                            "Compare bladeless Femto-LASIK, SMILE recovery timelines, HSA/FSA tax savings, and lifetime enhancement policies.",
                            "Methodenvergleich Femto-LASIK vs. SMILE, Heilungsdauer, Qualitätszertifikate und transparente Klinikpreise.",
                            "Comparatif Femto-LASIK et SMILE, récupération visuelle, remboursements mutuelle et cliniques certifiées.",
                            List.of("Ditch Glasses in 2026: Compare LASIK & SMILE Surgery Costs", "Use HSA/FSA Funds & 0% Financing for Bladeless Vision Care", "See Recovery Timelines & Certified Surgeon Checklists"),
                            List.of("Brillenfrei 2026: Augenlasern-Methoden & Kosten pro Auge", "Femto-LASIK vs. SMILE — Heilungszeit und Sicherheit", "Steuerliche Absetzbarkeit und Klinik-Checkliste"),
                            List.of("Opération Myopie Laser 2026 : Prix Par Œil et Mutuelles", "LASIK vs SMILE : Comparatif Précision et Récupération", "Tout Savoir Avant Votre Bilan Pré-Opératoire"))
            ))
    );

    private static final List<FeedbackSeed> FEEDBACK_EN = List.of(
            new FeedbackSeed(5, "The side-by-side spec table saved me hours of research before ordering."),
            new FeedbackSeed(5, "Very clear cost breakdown—appreciated seeing the hidden fees listed upfront."),
            new FeedbackSeed(4, "Helpful comparison guide. Would love to see a 6-month follow-up update too."),
            new FeedbackSeed(5, "Checked the rebate section for my state and found an extra $600 credit."),
            new FeedbackSeed(4, "Clean layout on mobile and fast loading without annoying popups."),
            new FeedbackSeed(5, "Finally an article that explains warranty exclusions in plain English."),
            new FeedbackSeed(4, "Good overview of budget vs flagship models; helped narrow it down to two picks."),
            new FeedbackSeed(3, "Solid guide overall, though two of the prices went up slightly this month."),
            new FeedbackSeed(5, "Shared this checklist with my parents—super easy to read and compare."),
            new FeedbackSeed(4, "Clear pros and cons without feeling like a generic sales pitch."),
            new FeedbackSeed(2, "Wish you included more regional providers for smaller rural zip codes."),
            new FeedbackSeed(5, "Accurate numbers and realistic payback estimates—bookmarked for later.")
    );

    private static final List<FeedbackSeed> FEEDBACK_DE = List.of(
            new FeedbackSeed(5, "Sehr übersichtlicher Vergleich, besonders die Hinweise zu den Förderungen 2026."),
            new FeedbackSeed(4, "Verständlich geschrieben und gute Tabelle für den schnellen Preis-Check."),
            new FeedbackSeed(5, "Endlich ein Ratgeber ohne Fachchinesisch — hat mir bei der Auswahl sehr geholfen."),
            new FeedbackSeed(3, "Gute Übersicht, ich würde mir noch mehr Erfahrungswerte nach einem Jahr wünschen.")
    );

    private static final List<FeedbackSeed> FEEDBACK_FR = List.of(
            new FeedbackSeed(5, "Comparatif très clair, les tableaux de prix réels évitent les mauvaises surprises."),
            new FeedbackSeed(4, "Guide bien structuré et agréable à lire sur mobile, merci pour les détails sur les aides."),
            new FeedbackSeed(5, "Exactement les informations pratiques que je cherchais avant de demander un devis."),
            new FeedbackSeed(3, "Article utile, certains tarifs ont légèrement évolué ce trimestre.")
    );

    record FeedbackSeed(int rate, String text) {}

    private PlatformEconomics() {}

    static String siteCode(int siteIndex) {
        return SiteController.sites().get(siteIndex);
    }

    static VerticalSpec vertical(int siteIndex) {
        return VERTICALS.get(siteIndex);
    }

    static AccountSpec account(int siteIndex) {
        return AD_ACCOUNTS.get(siteIndex);
    }

    static CountrySpec countryForSlot(int slot) {
        return SLOT_COUNTRIES.get(slot % SLOT_COUNTRIES.size());
    }

    static Subtopic subtopic(int siteIndex, int slot) {
        List<Subtopic> list = vertical(siteIndex).subtopics();
        return list.get(slot % list.size());
    }

    static String operatorForCampaign(int siteIndex, int slot) {
        String site = siteCode(siteIndex);
        List<OperatorSpec> eligible = OPERATORS.stream()
                .filter(op -> op.sites().contains(site))
                .toList();
        if (eligible.isEmpty()) return OPERATORS.get(siteIndex % OPERATORS.size()).name();
        return eligible.get(slot % eligible.size()).name();
    }

    static String platformPrefix(String platform) {
        return switch (platform) {
            case "TikTok" -> "TT";
            case "Taboola" -> "TB";
            case "Google" -> "GG";
            case "Facebook" -> "FB";
            default -> "AD";
        };
    }

    static String platformObjective(String platform, int slot) {
        return switch (platform) {
            case "TikTok" -> slot % 2 == 0 ? "RSOC" : "Conv";
            case "Taboola" -> "Native";
            case "Google" -> slot % 2 == 0 ? "Search" : "PMax";
            case "Facebook" -> slot % 2 == 0 ? "CBO" : "ABO";
            default -> "Perf";
        };
    }

    static String campaignName(int siteIndex, int slot) {
        VerticalSpec v = vertical(siteIndex);
        Subtopic s = subtopic(siteIndex, slot);
        CountrySpec c = countryForSlot(slot);
        int seq = siteIndex * SLOTS_PER_SITE + slot + 1;
        return "%s_%s_%s_%s_%03d".formatted(platformPrefix(v.platform()), c.code(), s.slug(),
                platformObjective(v.platform(), slot), seq);
    }

    static String articleTitle(int siteIndex, int slot) {
        Subtopic s = subtopic(siteIndex, slot);
        CountrySpec c = countryForSlot(slot);
        String base = switch (c.lang()) {
            case "de" -> s.titleDe();
            case "fr" -> s.titleFr();
            default -> s.titleEn();
        };
        int wave = slot / 5;
        if (wave == 0) return base;
        String suffix = switch (c.lang()) {
            case "de" -> switch (wave) {
                case 1 -> " · Preis-Leistungs-Check";
                case 2 -> " · Kaufberatung";
                default -> " · Aktuelle Angebote";
            };
            case "fr" -> switch (wave) {
                case 1 -> " · Guide Pratique";
                case 2 -> " · Meilleurs Choix";
                default -> " · Offres Actuelles";
            };
            default -> switch (wave) {
                case 1 -> " — Side-by-Side Comparison";
                case 2 -> " — Buyer's Checklist";
                default -> " — Budget & Value Picks";
            };
        };
        return base + suffix;
    }

    static String articleLead(int siteIndex, int slot) {
        Subtopic s = subtopic(siteIndex, slot);
        CountrySpec c = countryForSlot(slot);
        return switch (c.lang()) {
            case "de" -> s.leadDe();
            case "fr" -> s.leadFr();
            default -> s.leadEn();
        };
    }

    static List<String> articleParagraphs(int siteIndex, int slot) {
        String lead = articleLead(siteIndex, slot);
        CountrySpec c = countryForSlot(slot);
        return switch (c.lang()) {
            case "de" -> List.of(
                    lead,
                    "Vergleichen Sie im ersten Schritt laufende Kosten, Garantiebedingungen und echte Nutzerbewertungen für den deutschen Markt.",
                    "Prüfen Sie vor dem Abschluss immer aktuelle Aktionskonditionen, regionale Zuschüsse und flexible Kündigungsfristen.");
            case "fr" -> List.of(
                    lead,
                    "Comparez d'abord le coût total sur 12 mois, les garanties incluses et les avis vérifiés d'utilisateurs en France.",
                    "Vérifiez les aides disponibles et demandez deux estimations chiffrées avant de valider votre choix final.");
            default -> List.of(
                    lead,
                    "Start by comparing total 12-month ownership cost, warranty terms, and independent buyer reviews for " + c.code() + " households.",
                    "Always verify current regional promotions, transparent fee disclosures, and cancellation terms before committing.");
        };
    }

    static List<String> adCopy(int siteIndex, int slot) {
        Subtopic s = subtopic(siteIndex, slot);
        CountrySpec c = countryForSlot(slot);
        return switch (c.lang()) {
            case "de" -> s.copyDe();
            case "fr" -> s.copyFr();
            default -> s.copyEn();
        };
    }

    static String landingPageUrl(int siteIndex, int slot, String channelId) {
        String site = siteCode(siteIndex);
        String slug = subtopic(siteIndex, slot).slug()
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase(Locale.ROOT);
        int seq = siteIndex * SLOTS_PER_SITE + slot + 1;
        // Keep well below taboola_data.landing_url VARCHAR(128) limit and ensure AppUtils.getSite() extracts exact site code.
        return "https://%s.com/p/%s-%03d?channel=%s".formatted(site, slug, seq, channelId);
    }

    static String campaignState(int slot) {
        if (slot <= 16) return "PASS";
        if (slot == 17) return "AUDITING";
        if (slot == 18) return "REJECT";
        return "ARCHIVE";
    }

    static boolean isRunningActive(int slot) {
        return slot <= 15;
    }

    static boolean isReadyToPublish(int slot) {
        return slot == 16;
    }

    static boolean isRuleIntentionallyUnbound(int slot) {
        // Leave 1 active high-spend campaign per site (12 total) unbound so AI Anomaly Scanner detects RULE_UNBOUND!
        return slot == 15;
    }

    static String ruleKeyForCampaign(int siteIndex, int slot) {
        if (isRuleIntentionallyUnbound(slot) || slot == 17 || slot == 18) return null;
        String platform = vertical(siteIndex).platform();
        int bucket = slot % 4;
        return switch (platform) {
            case "TikTok" -> switch (bucket) {
                case 0 -> "tt-scale";
                case 1 -> "tt-stop-loss";
                case 2 -> "tt-zero";
                default -> "cold-start";
            };
            case "Facebook" -> switch (bucket) {
                case 0 -> "fb-scale";
                case 1 -> "fb-reduce";
                case 2 -> "fb-profit-guard";
                default -> "cold-start";
            };
            case "Google" -> switch (bucket) {
                case 0 -> "gg-scale";
                case 1 -> "gg-reduce";
                case 2 -> "gg-zero";
                default -> "cold-start";
            };
            default -> switch (bucket) {
                case 0 -> "tb-scale";
                case 1 -> "tb-reduce";
                default -> "cold-start";
            };
        };
    }

    static CreativeSpec creativeForCampaign(int siteIndex, int slot) {
        String platform = vertical(siteIndex).platform();
        int seq = siteIndex * SLOTS_PER_SITE + slot;
        return switch (platform) {
            case "TikTok" -> {
                String tmpl = switch (slot % 3) {
                    case 0 -> "tiktok-ugc-hook";
                    case 1 -> "tiktok-split-review";
                    default -> "tiktok-checklist-reel";
                };
                yield new CreativeSpec("VIDEO", "1080x1920", "9:16", 15 + (seq % 16),
                        4_200_000 + (seq % 17) * 480_000, tmpl, "mp4");
            }
            case "Facebook" -> {
                if (slot % 5 < 2) {
                    yield new CreativeSpec("VIDEO", "1080x1350", "4:5", 14 + (seq % 12),
                            3_100_000 + (seq % 13) * 420_000, "meta-feed-reel", "mp4");
                }
                String tmpl = slot % 2 == 0 ? "meta-carousel-card" : "meta-comparison-grid";
                yield new CreativeSpec("IMAGE", "1080x1080", "1:1", 0,
                        260_000 + (seq % 19) * 21_500, tmpl, "jpg");
            }
            case "Google" -> {
                boolean square = slot % 3 == 0;
                yield new CreativeSpec("IMAGE", square ? "1200x1200" : "1200x628",
                        square ? "1:1" : "1.91:1", 0,
                        195_000 + (seq % 15) * 18_400,
                        square ? "google-rda-square" : "google-rda-landscape", "jpg");
            }
            default -> new CreativeSpec("IMAGE", "1200x628", "1.91:1", 0,
                    165_000 + (seq % 17) * 14_200, "taboola-editorial-thumb", "jpg");
        };
    }

    static String materialUrl(int siteIndex, String materialId, CreativeSpec spec) {
        String site = siteCode(siteIndex);
        String plat = vertical(siteIndex).platform().toLowerCase(Locale.ROOT);
        return "https://cdn.%s.com/creatives/%s/%s.%s".formatted(site, plat, materialId, spec.ext());
    }

    static String materialCover(int siteIndex, String materialId, CreativeSpec spec) {
        if (!"VIDEO".equals(spec.type())) return "";
        String site = siteCode(siteIndex);
        String plat = vertical(siteIndex).platform().toLowerCase(Locale.ROOT);
        return "https://cdn.%s.com/creatives/%s/%s-thumb.jpg".formatted(site, plat, materialId);
    }

    static FeedbackSeed feedbackFor(String lang, int index) {
        return switch (lang) {
            case "de" -> FEEDBACK_DE.get(index % FEEDBACK_DE.size());
            case "fr" -> FEEDBACK_FR.get(index % FEEDBACK_FR.size());
            default -> FEEDBACK_EN.get(index % FEEDBACK_EN.size());
        };
    }

    /**
     * Generates mathematically consistent, platform-authentic 14-day AFS metrics for a single campaign.
     * Guarantees:
     * <ul>
     *   <li>{@code ad_impressions >= ad_clicks >= funnel_impressions >= funnel_clicks >= serp_impressions >= afs_clicks}</li>
     *   <li>{@code riskValue = afs_clicks / ad_conversions} in [0.94, 1.09]</li>
     *   <li>Realistic platform CPM / CTR / CPC & realistic portfolio ROAS distribution (0.62x - 1.72x)</li>
     *   <li>Smooth day-over-day temporal curves with weekday/weekend & creative-fatigue dynamics</li>
     * </ul>
     */
    static List<DailyRow> generateDailySeries(int siteIndex, int slot, LocalDate start, int days) {
        VerticalSpec v = vertical(siteIndex);
        CountrySpec c = countryForSlot(slot);
        int seq = siteIndex * SLOTS_PER_SITE + slot;
        Random rng = new Random(20261121L + seq * 997L);

        // Platform base economics (2025-2026 overseas AFS arbitrage).
        double baseDailySpend;
        double baseCtr;
        double baseCpc;
        switch (v.platform()) {
            case "TikTok" -> {
                baseDailySpend = 155.0;
                baseCtr = 0.0148;
                baseCpc = 0.37;
            }
            case "Taboola" -> {
                baseDailySpend = 125.0;
                baseCtr = 0.0044;
                baseCpc = 0.24;
            }
            case "Google" -> {
                baseDailySpend = 205.0;
                baseCtr = 0.0385;
                baseCpc = 0.72;
            }
            default -> { // Facebook / Meta
                baseDailySpend = 185.0;
                baseCtr = 0.0168;
                baseCpc = 0.50;
            }
        }

        // Publisher-level realism: not all 12 publishers have identical ROAS!
        double publisherShift = switch (siteIndex % 6) {
            case 0 -> 0.06;
            case 1 -> -0.04;
            case 2 -> 0.09;
            case 3 -> -0.07;
            case 4 -> 0.03;
            default -> -0.02;
        };

        // Campaign archetype (across active slots 0..15):
        // ~28% scaling winners (1.36-1.68), ~40% core healthy (1.06-1.34),
        // ~18% soft/fatiguing (0.86-1.05), ~14% bleeding stop-loss (0.64-0.84).
        int archetypeBucket = (slot * 7 + siteIndex * 3) % 16;
        double targetBaseRoas;
        double dailyRoasSlope;
        double scaleMult;
        if (archetypeBucket <= 4) { // Winner (5/16)
            targetBaseRoas = 1.38 + rng.nextDouble() * 0.28 + publisherShift * 0.5;
            dailyRoasSlope = 0.003;
            scaleMult = 1.15 + rng.nextDouble() * 0.55;
        } else if (archetypeBucket <= 10) { // Core profitable (6/16)
            targetBaseRoas = 1.08 + rng.nextDouble() * 0.26 + publisherShift * 0.5;
            dailyRoasSlope = (rng.nextDouble() - 0.5) * 0.004;
            scaleMult = 0.80 + rng.nextDouble() * 0.55;
        } else if (archetypeBucket <= 13) { // Soft / fatiguing (3/16)
            targetBaseRoas = 0.94 + rng.nextDouble() * 0.14 + publisherShift * 0.3;
            dailyRoasSlope = -0.014; // genuine 14-day fatigue curve for AI diagnostics!
            scaleMult = 0.70 + rng.nextDouble() * 0.45;
        } else { // Bleeding stop-loss candidate (2/16)
            targetBaseRoas = 0.68 + rng.nextDouble() * 0.16;
            dailyRoasSlope = -0.009;
            scaleMult = 0.65 + rng.nextDouble() * 0.50;
        }
        targetBaseRoas = clamp(targetBaseRoas, 0.60, 1.74);

        double campaignCtrFactor = 0.85 + rng.nextDouble() * 0.32;
        double campaignCpcFactor = 0.86 + rng.nextDouble() * 0.28;
        double baseLpLoad = 0.81 + rng.nextDouble() * 0.07;       // 81% - 88% click-to-LP load
        double baseRsCtr = 0.46 + rng.nextDouble() * 0.16;        // 46% - 62% RSOC Related Search CTR
        double baseSerpMatch = 0.93 + rng.nextDouble() * 0.05;    // 93% - 98% SERP matched ads
        double baseSerpCtr = 0.58 + rng.nextDouble() * 0.18;      // 58% - 76% SERP paid click CTR

        List<DailyRow> rows = new ArrayList<>(days);
        for (int d = 0; d < days; d++) {
            LocalDate date = start.plusDays(d);
            DayOfWeek dow = date.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            double dowVol = weekend
                    ? (v.categorySlug().contains("finance") || v.categorySlug().contains("office") ? 0.86 : 1.07)
                    : (dow == DayOfWeek.TUESDAY || dow == DayOfWeek.WEDNESDAY ? 1.05 : 1.00);
            double dowRpm = weekend
                    ? (v.categorySlug().contains("finance") || v.categorySlug().contains("insurance") ? 0.94 : 1.02)
                    : 1.01;

            double trendVol = 1.0 + (dailyRoasSlope > 0 ? 0.012 : dailyRoasSlope < -0.008 ? -0.006 : 0.002) * (d - days / 2.0);
            double volNoise = 0.92 + rng.nextDouble() * 0.16;
            double cpcNoise = 0.95 + rng.nextDouble() * 0.10;
            double ctrNoise = 0.94 + rng.nextDouble() * 0.12;
            double roasNoise = 0.93 + rng.nextDouble() * 0.14;

            double rawSpend = baseDailySpend * c.volMult() * scaleMult * dowVol * trendVol * volNoise;
            rawSpend = Math.max(42.0, rawSpend);

            double dayCpc = baseCpc * c.cpcMult() * campaignCpcFactor * cpcNoise
                    * (dailyRoasSlope < -0.008 ? 1.0 + 0.006 * d : 1.0);
            long adClicks = Math.max(25L, Math.round(rawSpend / dayCpc));
            BigDecimal adSpend = BigDecimal.valueOf(adClicks * dayCpc).setScale(2, RoundingMode.HALF_UP);

            double fatigueCtr = dailyRoasSlope < -0.008 ? Math.max(0.78, 1.0 - 0.012 * d) : 1.0;
            double dayCtr = clamp(baseCtr * campaignCtrFactor * ctrNoise * fatigueCtr, 0.0022, 0.068);
            long adImpressions = Math.max(adClicks * 12L, Math.round(adClicks / dayCtr));

            // Two-click AFS funnel (monotonically decreasing: adClicks >= funnelImp >= funnelClicks >= serpImp >= afsClicks)
            long funnelImp = clampLong(Math.round(adClicks * clamp(baseLpLoad + (rng.nextDouble() - 0.5) * 0.03, 0.76, 0.91)),
                    1L, adClicks);
            long funnelClicks = clampLong(Math.round(funnelImp * clamp(baseRsCtr + (rng.nextDouble() - 0.5) * 0.04, 0.36, 0.68)),
                    1L, funnelImp);
            long serpImp = clampLong(Math.round(funnelClicks * clamp(baseSerpMatch + (rng.nextDouble() - 0.5) * 0.02, 0.89, 0.99)),
                    1L, funnelClicks);
            long afsClicks = clampLong(Math.round(serpImp * clamp(baseSerpCtr + (rng.nextDouble() - 0.5) * 0.05, 0.45, 0.82)),
                    1L, serpImp);

            // Pixel S2S conversions track SERP paid clicks closely -> riskValue = afsClicks / adConversions ~ 0.95 - 1.08.
            double pixelMatch = 0.94 + rng.nextDouble() * 0.12;
            long adConversions = clampLong(Math.round(afsClicks * pixelMatch), 1L, adClicks);

            double dayRoas = clamp((targetBaseRoas + dailyRoasSlope * (d - days / 2.0)) * dowRpm * roasNoise, 0.52, 1.88);
            BigDecimal revenue = adSpend.multiply(BigDecimal.valueOf(dayRoas)).setScale(2, RoundingMode.HALF_UP);

            double rpc = afsClicks > 0 ? round2(revenue.doubleValue() / afsClicks) : 0.0;
            double afsCtr = serpImp > 0 ? round4((double) afsClicks / serpImp) : 0.0;
            double funnelRpm = funnelImp > 0 ? round2(1000.0 * revenue.doubleValue() / funnelImp) : 0.0;

            rows.add(new DailyRow(date, adImpressions, adClicks, adConversions, adSpend,
                    funnelImp, funnelClicks, serpImp, afsClicks, revenue, rpc, afsCtr, funnelRpm));
        }
        return rows;
    }

    static CampaignTotals summarize(List<DailyRow> rows) {
        long imp = 0, clicks = 0, conv = 0;
        BigDecimal spend = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        for (DailyRow row : rows) {
            imp += row.adImpressions();
            clicks += row.adClicks();
            conv += row.adConversions();
            spend = spend.add(row.adSpend());
            revenue = revenue.add(row.grossRevenue());
        }
        spend = spend.setScale(2, RoundingMode.HALF_UP);
        revenue = revenue.setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = revenue.subtract(spend).setScale(2, RoundingMode.HALF_UP);
        double roas = spend.signum() > 0 ? round4(revenue.doubleValue() / spend.doubleValue()) : 0.0;
        BigDecimal dailyBudget = BigDecimal.ZERO;
        if (!rows.isEmpty() && spend.signum() > 0) {
            double avgDaily = spend.doubleValue() / rows.size();
            double rounded = Math.ceil((avgDaily * 1.15) / 10.0) * 10.0;
            dailyBudget = BigDecimal.valueOf(Math.max(50.0, rounded)).setScale(2, RoundingMode.HALF_UP);
        }
        return new CampaignTotals(imp, clicks, conv, spend, revenue, profit, roas, dailyBudget);
    }

    private static Subtopic sub(String slug, String keyword, String titleEn, String titleDe, String titleFr,
                                String leadEn, String leadDe, String leadFr,
                                List<String> copyEn, List<String> copyDe, List<String> copyFr) {
        return new Subtopic(slug, keyword, titleEn, titleDe, titleFr, leadEn, leadDe, leadFr, copyEn, copyDe, copyFr);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
