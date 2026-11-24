package com.afs.agent;

import java.util.List;
import java.util.Locale;

/**
 * Editorial engine for article bodies and ad copy.
 *
 * <p>The legacy fixture reused one three-sentence body for every article and three generic
 * ad headlines per subtopic, which made 240 campaigns read like the same page duplicated.
 * This catalog gives every campaign a distinct editorial angle (comparison, checklist, budget,
 * hands-on test, fatigue update, mistakes, or regional pricing), a lead that quotes its own
 * measured numbers, and body paragraphs drawn from locale-specific pools so no two articles
 * share the same opening or closing.
 */
final class ContentLibrary {
    private ContentLibrary() {}

    // ------------------------------------------------------------------ angles

    record AngleSpec(String key, String suffixEn, String suffixDe, String suffixFr,
                     String leadEn, String leadDe, String leadFr,
                     String pitchEn, String pitchDe, String pitchFr,
                     String closeEn, String closeDe, String closeFr) {}

    private static final List<AngleSpec> ANGLES = List.of(
            new AngleSpec("side-by-side",
                    " — Side-by-Side Comparison",
                    " · Direktvergleich",
                    " — Comparatif Direct",
                    "We put {n} shortlisted options on the same bench: nothing here is a sponsored pick unless the data forced it.",
                    "Wir haben {n} Kandidaten auf dieselbe Prüfbank gestellt — keine Empfehlung ohne belastbare Messwerte.",
                    "Nous avons comparé {n} options sur le même banc : aucune sélection n'est guidée par un sponsor.",
                    "The table at the top of this page is the short version. Every row below explains what the number actually measured, on which device, and where a manufacturer's specification diverges from what we logged.",
                    "Die Tabelle oben ist die Kurzfassung. Jede Zeile darunter erklärt, was gemessen wurde, auf welchem Gerät — und wo Herstellerangaben von unseren Werten abweichen.",
                    "Le tableau en haut résume l'essentiel. Chaque ligne détaille la mesure réelle, l'appareil utilisé et les écarts avec les fiches constructeur.",
                    "If two options tie on the numbers, decide on service terms: return window, replacement parts and whether support answers in under a day.",
                    "Bei Punktgleichheit entscheiden die Servicebedingungen: Rückgabefrist, Ersatzteile und ob der Support binnen eines Tages antwortet.",
                    "En cas d'égalité, tranchez sur le service : délai de retour, pièces détachées et réactivité du support."),
            new AngleSpec("checklist",
                    " — Buyer's Checklist",
                    " · Checkliste vor dem Kauf",
                    " — Checkliste d'Achat",
                    "Before you spend anything, run this checklist: {n} of the most common returns in this category were avoidable at the point of purchase.",
                    "Vor dem Kauf diese Checkliste abarbeiten: {n} der häufigsten Rücksendungen wären beim Kauf vermeidbar gewesen.",
                    "Avant d'acheter, passez cette checklist : {n} des retours les plus fréquents étaient évitables au moment de l'achat.",
                    "A checklist is only useful if it changes a decision, so each item below ends with the specific action to take if it fails — walk away, size up, or ask for a written term.",
                    "Eine Checkliste hilft nur, wenn sie eine Entscheidung ändert. Jeder Punkt endet daher mit der konkreten Handlung: Abstand nehmen, größer wählen oder eine schriftliche Zusage verlangen.",
                    "Une checklist n'a de valeur que si elle change une décision : chaque point se termine par l'action à prendre — renoncer, prendre la taille au-dessus ou exiger un engagement écrit.",
                    "Print the shortlist and take it with you. Sales staff respond differently when you are reading written criteria rather than improvising.",
                    "Drucken Sie die Auswahlliste aus. Verkaufsgespräche laufen anders, wenn Sie schriftliche Kriterien abarbeiten statt spontan zu entscheiden.",
                    "Emportez la liste imprimée : un vendeur ne répond pas de la même façon face à des critères écrits."),
            new AngleSpec("budget",
                    " — Budget & Value Picks",
                    " · Preis-Leistungs-Sieger",
                    " — Meilleur Rapport Qualité-Prix",
                    "You do not need to spend {high} to get 90% of the performance here; these picks start around {low}, and we explain exactly what the extra money buys.",
                    "Für 90 % der Leistung müssen Sie keine {high} ausgeben: Diese Auswahl startet bei rund {low}, und wir zeigen genau, was der Aufpreis bringt.",
                    "Inutile de dépenser {high} pour obtenir 90 % des performances : cette sélection démarre vers {low}, et nous expliquons ce que paie le supplément.",
                    "Budget picks fail in predictable places: hinges, connectors, bearings and software support. We stress-tested exactly those parts rather than photographing the box.",
                    "Günstige Modelle scheitern an vorhersehbaren Stellen: Scharniere, Anschlüsse, Lager und Software-Support. Genau diese Teile haben wir belastet statt nur die Verpackung zu fotografieren.",
                    "Les modèles économiques cèdent toujours aux mêmes endroits : charnières, connecteurs, roulements et suivi logiciel. Nous avons testé ces pièces précisément.",
                    "Set a hard ceiling before you compare. Once the ceiling is fixed, the difference between the top two picks is usually service, not hardware.",
                    "Setzen Sie sich vor dem Vergleich eine harte Obergrenze. Danach entscheiden zwischen den besten zwei meist Service statt Technik.",
                    "Fixez un plafond avant de comparer : entre les deux meilleurs choix, la différence tient souvent au service, pas au matériel."),
            new AngleSpec("hands-on",
                    " — Hands-On Test Log",
                    " · Praxistest-Protokoll",
                    " — Journal de Test",
                    "This is a working test log, not a launch-day preview: {hours} hours of use across {n} units, with the failures left in the write-up.",
                    "Das ist ein Arbeitsprotokoll, keine Launch-Vorschau: {hours} Stunden Nutzung mit {n} Geräten, inklusive der Fehlschläge.",
                    "Journal de test réel, pas un aperçu de lancement : {hours} heures d'usage sur {n} unités, échecs inclus.",
                    "Where something broke, you will see the date and the conditions. Where a claim could not be reproduced, we say so instead of repeating the marketing copy.",
                    "Wo etwas ausfiel, stehen Datum und Umstände. Wo eine Herstellerangabe nicht reproduzierbar war, schreiben wir das — statt Werbetext zu wiederholen.",
                    "Quand une pièce a lâché, la date et les conditions figurent ici. Quand une promesse n'était pas reproductible, nous l'écrivons.",
                    "If your use case differs from ours — heavier load, longer sessions, shared household — weight the durability notes more heavily than the scores.",
                    "Wenn Ihre Nutzung abweicht — höhere Last, längere Sessions, mehrere Nutzer — gewichten Sie die Haltbarkeitsnotizen stärker als die Punkte.",
                    "Si votre usage diffère — charge plus lourde, sessions longues, foyer partagé — accordez plus de poids aux notes de durabilité qu'aux scores."),
            new AngleSpec("fatigue",
                    " — 90-Day Fatigue Update",
                    " · Update nach 90 Tagen",
                    " — Bilan Après 90 Jours",
                    "Ninety days in, the numbers have moved: performance drifted in some units and held in others, and the reasons are more interesting than the rankings.",
                    "Nach 90 Tagen haben sich die Werte verschoben: Bei einigen Geräten sank die Leistung, bei anderen blieb sie stabil — die Gründe sind aufschlussreicher als das Ranking.",
                    "Après 90 jours, les chiffres ont bougé : certaines unités ont perdu en performance, d'autres ont tenu, et les raisons comptent plus que le classement.",
                    "We re-ran the same measurements under the same conditions. Anything that changed by more than 8% is called out with the likely cause, from filter loading to firmware updates.",
                    "Wir haben dieselben Messungen unter gleichen Bedingungen wiederholt. Alles mit mehr als 8 % Abweichung ist markiert, inklusive wahrscheinlicher Ursache — von Filterbeladung bis Firmware-Update.",
                    "Nous avons refait les mêmes mesures dans les mêmes conditions. Tout écart supérieur à 8 % est signalé avec sa cause probable, du colmatage des filtres aux mises à jour.",
                    "A product that starts strong and fades is a worse buy than a steady mid-performer, so read the trend column before the headline score.",
                    "Ein stark startendes, schnell nachlassendes Produkt ist der schlechtere Kauf als ein konstantes Mittelklassegerät — lesen Sie die Trendspalte vor der Gesamtnote.",
                    "Un produit qui démarre fort puis décline est un moins bon achat qu'un modèle régulier : lisez la colonne tendance avant la note globale."),
            new AngleSpec("mistakes",
                    " — 7 Costly Mistakes",
                    " · 7 teure Fehler",
                    " — 7 Erreurs Coûteuses",
                    "Most of the money lost in this category is lost before delivery: {n} avoidable mistakes account for the majority of the complaints we reviewed.",
                    "Das meiste Geld geht vor der Lieferung verloren: {n} vermeidbare Fehler erklären den Großteil der Beschwerden, die wir ausgewertet haben.",
                    "L'essentiel de l'argent perdu dans cette catégorie se perd avant la livraison : {n} erreurs évitables expliquent la majorité des réclamations.",
                    "Each mistake below comes from a real support thread or invoice, with the fix stated in one sentence so you can check it in under a minute.",
                    "Jeder Fehler stammt aus einem echten Support-Thread oder einer Rechnung, inklusive Lösung in einem Satz — prüfbar in unter einer Minute.",
                    "Chaque erreur provient d'un fil de support ou d'une facture réelle, avec la correction en une phrase, vérifiable en moins d'une minute.",
                    "The cheapest protection is written confirmation of the term you are relying on. If it is not in the quote, assume it does not exist.",
                    "Der günstigste Schutz ist die schriftliche Bestätigung der Bedingung, auf die Sie sich verlassen. Steht sie nicht im Angebot, existiert sie nicht.",
                    "La protection la moins chère reste une confirmation écrite. Si la condition n'est pas dans le devis, considérez qu'elle n'existe pas."),
            new AngleSpec("regional",
                    " — Regional Pricing Report",
                    " · Regionaler Preisreport",
                    " — Rapport de Prix Régional",
                    "The same product is priced differently across {market}: local duties, warranty rules and stock levels explain most of the gap we measured this week.",
                    "Dasselbe Produkt kostet in {market} unterschiedlich viel: Zölle, Garantiebedingungen und Lagerbestand erklären einen Großteil der Differenz.",
                    "Le même produit n'a pas le même prix selon les régions de {market} : taxes locales, garanties et stocks expliquent l'essentiel de l'écart.",
                    "We collected delivered prices, not shelf prices, and normalised each one for warranty length so the comparison survives a cross-border purchase.",
                    "Wir haben Endpreise inklusive Lieferung erfasst und auf die Garantielänge normalisiert, damit der Vergleich auch bei grenzüberschreitendem Kauf trägt.",
                    "Nous avons relevé les prix livrés, pas les prix affichés, puis normalisé selon la durée de garantie pour un achat transfrontalier.",
                    "Cross-border buying only pays when the saving clears the return-shipping cost. Below that threshold, buy locally and keep the warranty simple.",
                    "Grenzüberschreitender Kauf lohnt erst, wenn die Ersparnis die Rücksendekosten übersteigt. Darunter lokal kaufen und die Garantie einfach halten.",
                    "L'achat transfrontalier ne vaut le coup que si l'économie dépasse le coût d'un retour. En dessous, achetez localement."),
            new AngleSpec("install",
                    " — Installation & Setup Notes",
                    " · Montage & Einrichtung",
                    " — Installation & Mise en Route",
                    "The spec sheet rarely mentions the installation: {n} of these units needed extra parts, and two required a second person to mount safely.",
                    "Das Datenblatt erwähnt die Montage selten: {n} Geräte brauchten Zubehör, zwei erforderten eine zweite Person für sichere Montage.",
                    "La fiche technique omet l'installation : {n} unités ont exigé des pièces en plus, et deux une seconde personne pour un montage sûr.",
                    "Setup time below is measured end-to-end, from unboxing to first successful use, including app pairing and any drilling — not just the assembly step.",
                    "Die Montagezeit ist von Auspacken bis zur ersten erfolgreichen Nutzung gemessen, inklusive App-Kopplung und Bohrungen — nicht nur der Aufbau.",
                    "Le temps d'installation est mesuré de l'ouverture du carton au premier usage réussi, appairage d'application et perçages inclus.",
                    "If you rent, check the fixing method before you buy: two of the strongest options here assume you can drill into masonry.",
                    "Wer zur Miete wohnt, prüft vor dem Kauf die Befestigung: Zwei der stärksten Optionen setzen Bohren in Mauerwerk voraus.",
                    "En location, vérifiez la fixation avant d'acheter : deux des meilleures options supposent de percer la maçonnerie."));

    // -------------------------------------------------------------- paragraph pools

    private static final List<String> METHOD_EN = List.of(
            "Method: every sample was bought at retail with our own money, run through the same five-step protocol, and re-tested after a week of normal use so that first-day impressions could not skew the result.",
            "Method: we used the same test rig for every unit, recorded ambient conditions, and repeated the two most variable measurements three times before averaging them.",
            "Method: retail units only — no vendor-supplied samples. Anything that arrived pre-configured or pre-installed was reset to factory defaults before testing.",
            "Method: the test protocol was written before the first unit arrived, so criteria could not be adjusted after we saw which brand performed well.",
            "Method: measurements were logged automatically where possible, and every manual reading was taken by two people independently to catch transcription errors.",
            "Method: we ran the full protocol twice, two weeks apart, and report the second run here so that break-in effects are already included.");

    private static final List<String> METHOD_DE = List.of(
            "Methode: Alle Testgeräte wurden regulär gekauft, nach identischem Fünf-Schritt-Protokoll geprüft und nach einer Woche Alltagsnutzung erneut gemessen, damit der erste Eindruck das Ergebnis nicht verzerrt.",
            "Methode: Für jedes Gerät kam derselbe Prüfstand zum Einsatz, Umgebungsbedingungen wurden protokolliert und die zwei schwankungsanfälligsten Messungen dreimal wiederholt.",
            "Methode: ausschließlich Handelssware, keine Herstellermuster. Vorkonfigurierte Geräte wurden vor dem Test auf Werkseinstellungen zurückgesetzt.",
            "Methode: Das Protokoll stand fest, bevor das erste Gerät eintraf — Kriterien wurden nicht nachträglich angepasst.",
            "Methode: Messwerte wurden möglichst automatisch erfasst; manuelle Ablesungen nahmen zwei Personen unabhängig voneinander vor.",
            "Methode: Das Protokoll lief zweimal im Abstand von zwei Wochen; hier steht der zweite Lauf, damit Einlaufeffekte enthalten sind.");

    private static final List<String> METHOD_FR = List.of(
            "Méthode : chaque exemplaire a été acheté au prix public, testé selon le même protocole en cinq étapes, puis remesuré après une semaine d'usage réel pour éviter l'effet de première impression.",
            "Méthode : même banc de test pour chaque unité, conditions ambiantes relevées, et les deux mesures les plus variables répétées trois fois avant moyenne.",
            "Méthode : uniquement des unités de vente, aucun exemplaire fourni par la marque. Tout appareil préconfiguré a été réinitialisé avant le test.",
            "Méthode : le protocole a été figé avant la réception du premier appareil, afin de ne pas ajuster les critères après coup.",
            "Méthode : mesures enregistrées automatiquement quand c'était possible, chaque relevé manuel effectué par deux personnes indépendamment.",
            "Méthode : protocole exécuté deux fois à quinze jours d'intervalle ; c'est le second passage qui figure ici, rodage inclus.");

    private static final List<String> COST_EN = List.of(
            "Total cost of ownership is where the ranking usually changes. Add consumables, replacement parts and the labour you will realistically do yourself, then divide by the years you expect to keep it — the spread between the cheapest sticker and the cheapest five-year cost is routinely 30% or more.",
            "Sticker price hides the second invoice: filters, belts, blades, subscriptions and delivery for spares. We priced a realistic three-year consumable basket for each option, and it moved four of the eight candidates by at least one rank.",
            "Budget the accessories before the device. Mounting hardware, adapters and the one cable the box does not include are frequently 12-20% of the purchase price, and they are rarely discounted later.",
            "Warranty length is a cost line, not a marketing badge. A two-year extension is worth roughly 6-9% of the purchase price in expected repair cost, so an option that includes it can be cheaper at the same sticker.",
            "Sell-on value changes the maths for anything you are likely to replace within four years. The best-performing options here also held resale value, which cuts the real ownership cost by a further 15-25%.",
            "Energy and water use matter more than most buyers expect. We measured consumption under realistic load and converted it to annual cost at current local tariffs rather than quoting the label figure.");

    private static final List<String> COST_DE = List.of(
            "Die Gesamtkosten entscheiden meist über das Ranking: Verbrauchsmaterial, Ersatzteile und realistische Eigenleistung addieren, dann durch die geplante Nutzungsdauer teilen — zwischen günstigstem Preis und günstigsten Fünf-Jahres-Kosten liegen oft 30 % oder mehr.",
            "Der Kaufpreis verdeckt die zweite Rechnung: Filter, Riemen, Klingen, Abos und Ersatzteilversand. Wir haben für jede Option einen realistischen Drei-Jahres-Bedarf kalkuliert — vier von acht Kandidaten verschoben sich dadurch um mindestens einen Rang.",
            "Kalkulieren Sie das Zubehör vor dem Gerät. Halterungen, Adapter und das fehlende Kabel machen häufig 12–20 % des Kaufpreises aus und werden später selten günstiger.",
            "Die Garantielänge ist eine Kostenposition, kein Werbeabzeichen. Zwei zusätzliche Jahre entsprechen etwa 6–9 % des Kaufpreises an erwarteten Reparaturkosten — bei gleichem Preis kann die längere Garantie den Ausschlag geben.",
            "Der Wiederverkaufswert verändert die Rechnung bei allem, was Sie in vier Jahren ersetzen. Die besten Optionen hier hielten ihren Restwert und senken die realen Besitzkosten um weitere 15–25 %.",
            "Strom- und Wasserverbrauch werden unterschätzt. Wir haben den Verbrauch unter realistischer Last gemessen und mit aktuellen lokalen Tarifen in Jahreskosten umgerechnet, statt das Label zu zitieren.");

    private static final List<String> COST_FR = List.of(
            "Le coût total de possession change souvent le classement : consommables, pièces, main-d'œuvre réaliste, puis division par la durée de conservation — l'écart entre le prix affiché et le coût réel sur cinq ans dépasse régulièrement 30 %.",
            "Le prix affiché cache la seconde facture : filtres, courroies, lames, abonnements et livraison des pièces. Nous avons chiffré un panier de consommables sur trois ans, et quatre candidats sur huit ont perdu au moins une place.",
            "Budgétez les accessoires avant l'appareil. Supports, adaptateurs et le câble absent de la boîte représentent souvent 12 à 20 % du prix, et sont rarement remisés ensuite.",
            "La durée de garantie est une ligne de coût. Deux années supplémentaires valent environ 6 à 9 % du prix en réparation attendue : à prix égal, elles peuvent départager deux options.",
            "La valeur de revente change le calcul de tout ce qui sera remplacé sous quatre ans. Les meilleures options conservent leur valeur, ce qui réduit le coût réel de 15 à 25 % de plus.",
            "Énergie et eau pèsent plus que prévu. Nous avons mesuré la consommation en charge réelle et l'avons convertie en coût annuel aux tarifs locaux, sans reprendre l'étiquette.");

    private static final List<String> VERIFY_EN = List.of(
            "Before you commit, confirm three things in writing: the exact model number, the return window including who pays shipping, and the warranty start date. Those three lines resolve most disputes we see after delivery.",
            "Ask for the model number rather than the series name. Retailers routinely substitute a near-identical variant with a smaller motor, fewer accessories or a shorter warranty, and the listing often keeps the same photos.",
            "Check the current firmware and app support status on the manufacturer's own support page, not the retailer's. Two options in this test had already stopped receiving security updates.",
            "If delivery matters, ask for a dated estimate in writing rather than a range. Dates are usually recoverable in a dispute; ranges are not.",
            "Take photos of the packaging before opening and of the serial plate after. It takes two minutes and settles most damage and warranty arguments without escalation.",
            "Confirm who performs service locally. An excellent device with no service partner within 100 km is a worse choice than a good device with same-week repair.");

    private static final List<String> VERIFY_DE = List.of(
            "Vor dem Kauf drei Dinge schriftlich bestätigen lassen: exakte Modellnummer, Rückgabefrist inklusive Versandkosten und Startdatum der Garantie. Diese drei Zeilen lösen die meisten Streitfälle nach der Lieferung.",
            "Fragen Sie nach der Modellnummer, nicht nach der Serie. Händler tauschen regelmäßig gegen eine fast identische Variante mit kleinerem Motor, weniger Zubehör oder kürzerer Garantie — oft mit denselben Fotos.",
            "Prüfen Sie Firmware- und App-Support auf der Herstellerseite, nicht beim Händler. Zwei Optionen in diesem Test erhielten bereits keine Sicherheitsupdates mehr.",
            "Wenn der Liefertermin zählt, lassen Sie sich ein datiertes Datum schriftlich geben statt einer Spanne. Daten sind im Streitfall durchsetzbar, Spannen nicht.",
            "Fotografieren Sie Verpackung vor dem Öffnen und Typenschild danach. Zwei Minuten, die die meisten Schadens- und Garantiefälle ohne Eskalation klären.",
            "Klären Sie, wer lokal wartet. Ein sehr gutes Gerät ohne Servicepartner in 100 km ist die schlechtere Wahl gegenüber einem guten Gerät mit Reparatur in derselben Woche.");

    private static final List<String> VERIFY_FR = List.of(
            "Avant de valider, faites confirmer par écrit trois éléments : la référence exacte, le délai de retour frais de renvoi inclus, et la date de début de garantie. Ces trois lignes règlent la majorité des litiges.",
            "Demandez la référence, pas le nom de gamme. Les revendeurs substituent régulièrement une variante presque identique, moteur plus petit ou garantie plus courte, en gardant les mêmes photos.",
            "Vérifiez le suivi logiciel sur le site du fabricant, pas chez le revendeur. Deux options de ce test ne recevaient déjà plus de mises à jour de sécurité.",
            "Si la livraison compte, exigez une date ferme par écrit plutôt qu'une fourchette : une date se défend en litige, une fourchette non.",
            "Photographiez l'emballage avant ouverture et la plaque de série ensuite. Deux minutes qui règlent la plupart des litiges de dommage et de garantie.",
            "Vérifiez qui assure le service localement. Un excellent appareil sans partenaire à 100 km est un moins bon choix qu'un bon appareil réparable la semaine même.");

    private static final List<String> MARKET_EN = List.of(
            "For {market} readers, availability is the real constraint: two of the strongest options ship from regional stock in 3-5 days, while the rest are 2-3 weeks with import handling. Check the plug type and voltage before ordering — adapters are not always included.",
            "Pricing in {market} moved after the last freight adjustment, so compare delivered totals rather than the listed price. Local returns are much cheaper to exercise, which changes the risk calculation on a first-time purchase.",
            "In {market}, warranty service runs through the importer rather than the manufacturer, and response times vary widely. We listed the local service partner for each option where one exists.",
            "Buyers in {market} should check the energy rating and any local incentive before choosing: for this category, the difference between the two best-rated models can exceed the price gap within three years.",
            "Stock levels in {market} have been uneven this quarter. If you need it now, filter by local availability first; if you can wait, the imported option is usually 12-18% cheaper delivered.",
            "Local installation rules in {market} matter more than the spec sheet for this category. Confirm the permit or certification requirement before delivery, because returns after installation are rarely accepted.");

    private static final List<String> MARKET_DE = List.of(
            "Für Leser in {market} ist die Verfügbarkeit die eigentliche Einschränkung: Zwei der stärksten Optionen kommen in 3–5 Tagen aus regionalem Lager, der Rest in 2–3 Wochen inklusive Importabwicklung. Stecker Typ und Spannung vor der Bestellung prüfen — Adapter liegen nicht immer bei.",
            "Die Preise in {market} haben sich nach der letzten Frachtanpassung verschoben. Vergleichen Sie Endpreise statt Listenpreise. Lokale Rücksendungen sind deutlich günstiger, was das Risiko beim Erstkauf verändert.",
            "In {market} läuft der Garantieservice über den Importeur, nicht über den Hersteller — die Reaktionszeiten unterscheiden sich stark. Wo vorhanden, nennen wir den lokalen Servicepartner.",
            "Käufer in {market} sollten Energieeffizienz und lokale Förderungen vor der Wahl prüfen: In dieser Kategorie kann die Differenz zwischen den zwei besten Modellen den Preisunterschied binnen drei Jahren übersteigen.",
            "Die Lagerbestände in {market} waren in diesem Quartal unregelmäßig. Wer sofort kaufen muss, filtert zuerst nach lokaler Verfügbarkeit; wer warten kann, spart bei der Importvariante meist 12–18 %.",
            "Lokale Montageregeln in {market} sind hier wichtiger als das Datenblatt. Prüfen Sie Zulassung oder Zertifizierung vor der Lieferung — nach der Montage wird selten zurückgenommen.");
    private static final List<String> MARKET_FR = List.of(
            "Pour les lecteurs de {market}, la disponibilité est la vraie contrainte : deux des meilleures options partent d'un stock régional en 3 à 5 jours, les autres demandent 2 à 3 semaines avec dédouanement. Vérifiez prise et tension avant de commander.",
            "Les prix en {market} ont bougé après le dernier ajustement fret : comparez les totaux livrés, pas les prix affichés. Un retour local coûte bien moins cher, ce qui change le risque d'un premier achat.",
            "En {market}, le service de garantie passe par l'importateur et les délais varient fortement. Nous indiquons le partenaire local lorsqu'il existe.",
            "Les acheteurs de {market} doivent vérifier la classe énergétique et les aides locales : dans cette catégorie, l'écart entre les deux meilleurs modèles peut dépasser la différence de prix en trois ans.",
            "Les stocks en {market} sont irréguliers ce trimestre. Pour un besoin immédiat, filtrez sur la disponibilité locale ; sinon, l'option importée revient souvent 12 à 18 % moins cher livrée.",
            "Les règles d'installation locales en {market} comptent plus que la fiche technique. Confirmez l'autorisation ou la certification avant livraison : après pose, les retours sont rarement acceptés.");

    record CopyPack(List<String> method, List<String> cost, List<String> verify, List<String> market) {}

    private static final CopyPack EN = new CopyPack(METHOD_EN, COST_EN, VERIFY_EN, MARKET_EN);
    private static final CopyPack DE = new CopyPack(METHOD_DE, COST_DE, VERIFY_DE, MARKET_DE);
    private static final CopyPack FR = new CopyPack(METHOD_FR, COST_FR, VERIFY_FR, MARKET_FR);

    record CampaignContent(String title, String lead, List<String> paragraphs, List<String> adCopy, String angle,
                           int productCount, int testHours, int lowPrice, int highPrice, int months, String market) {}

    /**
     * Builds a complete, campaign-specific article and ad set. Deterministic for a given
     * campaign so the fixture is reproducible across restarts.
     */
    static CampaignContent build(int siteIndex, int slot, String siteCode, String subtopicTitle,
                                 String subtopicLead, List<String> baseCopy, String keyword,
                                 String lang, String market, int sequence, int style, String platform) {
        boolean premium = style == 1;
        Locale locale = switch (lang == null ? "" : lang) {
            case "de" -> Locale.GERMAN;
            case "fr" -> Locale.FRENCH;
            default -> Locale.ENGLISH;
        };
        CopyPack pack = switch (locale.getLanguage()) {
            case "de" -> DE;
            case "fr" -> FR;
            default -> EN;
        };
        java.util.Random rng = new java.util.Random(7_919L * 31 + sequence * 1_009L + siteIndex * 37L);
        int angleIndex = Math.floorMod(sequence * 13 + siteIndex * 5 + slot, ANGLES.size());
        AngleSpec angle = ANGLES.get(angleIndex);

        int productCount = 5 + rng.nextInt(8);
        int testHours = 20 + rng.nextInt(60);
        // High-ticket services price in the hundreds/thousands, consumer goods in tens, and
        // advice content does not lead with a price at all.
        int lowPrice = premium ? (380 + rng.nextInt(1_600)) / 10 * 10 : (29 + rng.nextInt(120)) / 5 * 5;
        int highPrice = premium ? lowPrice * (2 + rng.nextInt(3)) : lowPrice * (4 + rng.nextInt(3));
        int months = 90 + rng.nextInt(120);

        // Four waves per subtopic per publisher: each wave is a genuinely different article,
        // not the same headline with a different suffix.
        int wave = Math.floorMod(slot, 20) / 5;
        String title = titleFor(locale, wave, angle, subtopicTitle, productCount,
                lowPrice, highPrice, months, market, sequence, style);
        String squad = localisedLead(locale, angle, productCount, testHours, lowPrice, highPrice, months, market);
        String pitch = switch (locale.getLanguage()) {
            case "de" -> angle.pitchDe();
            case "fr" -> angle.pitchFr();
            default -> angle.pitchEn();
        };
        String close = switch (locale.getLanguage()) {
            case "de" -> angle.closeDe();
            case "fr" -> angle.closeFr();
            default -> angle.closeEn();
        };

        // Article length is deliberately uneven: short buying notes and long guides coexist,
        // the way a real editorial calendar looks after a few months of publishing.
        List<String> optional = new java.util.ArrayList<>(4);
        optional.add(subtopicLead);
        optional.add(pick(pack.cost(), rng).replace("{market}", market));
        optional.add(pick(pack.market(), rng).replace("{market}", market));
        optional.add(close + " " + pick(pack.verify(), rng));
        java.util.Collections.shuffle(optional, new java.util.Random(sequence * 31L + angleIndex));
        int keep = 1 + Math.floorMod(sequence, 4);
        List<String> paragraphs = new java.util.ArrayList<>(3 + keep);
        paragraphs.add(squad);
        paragraphs.add(pick(pack.method(), rng));
        paragraphs.add(pitch);
        paragraphs.addAll(optional.subList(0, keep));

        // Ad copy: real ad groups carry 2-5 lines mixing a hook, a proof point and a CTA;
        // line count and wording both vary per campaign.
        List<String> adCopy = new java.util.ArrayList<>(5);
        adCopy.add(headlineFor(locale, angle, subtopicTitle, productCount, lowPrice, market, sequence));
        if (baseCopy != null && !baseCopy.isEmpty()) {
            String base = baseCopy.get(Math.floorMod(angleIndex + slot, baseCopy.size()));
            adCopy.add(localiseBaseHeadline(locale, base, productCount, market, 2026 + Math.floorMod(sequence, 2)));
            int lines = 2 + Math.floorMod(sequence, 4); // 2..5 lines
            int extra = 0;
            if (lines >= 3) {
                adCopy.add(tacticalHook(locale, angle, baseCopy.get(Math.floorMod(angleIndex + 1, baseCopy.size())),
                        productCount, lowPrice, market));
                extra++;
            }
            if (lines >= 4) {
                adCopy.add(proofLine(locale, productCount, testHours, months));
                extra++;
            }
            if (lines >= 5) {
                adCopy.add(ctaLine(locale, lowPrice, market));
            }
        }
        if (adCopy.size() < 2) {
            adCopy.add(headlineFor(locale, angle, subtopicTitle, productCount, lowPrice, market, sequence + 1));
        }
        adCopy = shapeForPlatform(locale, platform, adCopy, subjectOf(subtopicTitle), productCount,
                lowPrice, highPrice, market, sequence);

        return new CampaignContent(title, squad, List.copyOf(paragraphs), List.copyOf(adCopy),
                angle.key(), productCount, testHours, lowPrice, highPrice, months, market);
    }

    /**
     * Ad platforms constrain copy length, and a real account mixes short headlines with longer
     * primary text. Google RSA headlines are capped at 30 characters, Meta primary text usually
     * runs 90-140, TikTok ad text 40-80, Taboola titles 40-70.
     */
    private static List<String> shapeForPlatform(Locale locale, String platform, List<String> raw,
                                                 String subject, int productCount, int lowPrice, int highPrice,
                                                 String market, int sequence) {
        String plat = platform == null ? "" : platform;
        List<String> out = new java.util.ArrayList<>(4);
        String cta = ctaShort(locale, market);
        String first = raw.isEmpty() ? subject : raw.get(0);
        String second = raw.size() > 1 ? raw.get(1) : subject;
        List<String> ctaPool = new java.util.ArrayList<>(List.of(
                "Shop now", "See prices", "Compare picks", "Read the test", "Check prices"));
        switch (plat) {
            case "Google" -> {
                // RSA: up to 15 headlines of 30 chars plus 4 descriptions of 90.
                out.add(trimTo(locale, subject, 30));
                out.add(trimTo(locale, "Compare " + productCount + " Options", 30));
                out.add(trimTo(locale, first, 90));
                out.add(trimTo(locale, second, 90));
            }
            case "Facebook" -> {
                // Feed: one primary text (truncated around 125), a short headline, then CTA.
                out.add(trimTo(locale, first, 130));
                out.add(trimTo(locale, subject, 40));
                out.add(cta);
            }
            case "TikTok" -> {
                out.add(trimTo(locale, first, 60));
                out.add(trimTo(locale, second, 80));
                out.add(cta);
            }
            default -> {
                out.add(trimTo(locale, first, 70));
                out.add(trimTo(locale, second, 90));
                out.add(cta);
            }
        }
        return List.copyOf(out);
    }

    /** Truncates on a word boundary so platform limits never cut a word in half. */
    private static String trimTo(Locale locale, String value, int limit) {
        if (value == null) return "";
        String text = value.replaceAll("\\s+", " ").trim();
        if (text.length() <= limit) return text;
        int cut = text.lastIndexOf(' ', limit);
        if (cut < limit / 2) cut = limit;
        return text.substring(0, cut).trim();
    }

    private static String ctaShort(Locale locale, String market) {
        return switch (locale.getLanguage()) {
            case "de" -> "Jetzt Preise vergleichen";
            case "fr" -> "Voir les prix";
            default -> "Compare live prices";
        };
    }

    private static String proofLine(Locale locale, int productCount, int testHours, int months) {
        return switch (locale.getLanguage()) {
            case "de" -> "%d Modelle, %d Stunden Messung, %d Tage Nachbeobachtung — Zahlen statt Werbeversprechen."
                    .formatted(productCount, testHours, months);
            case "fr" -> "%d modèles, %d heures de mesure, %d jours de suivi — des chiffres, pas des promesses."
                    .formatted(productCount, testHours, months);
            default -> "%d units, %d hours of measurement, %d-day follow-up — numbers, not marketing lines."
                    .formatted(productCount, testHours, months);
        };
    }

    private static String ctaLine(Locale locale, int lowPrice, String market) {
        return switch (locale.getLanguage()) {
            case "de" -> "Preise ab %d $ vergleichen — Stand %s.".formatted(lowPrice, market);
            case "fr" -> "Comparez les prix dès %d $ — relevé pour %s.".formatted(lowPrice, market);
            default -> "Compare live prices from $%d — checked for %s.".formatted(lowPrice, market);
        };
    }

    /** Rewrites a reusable headline into a localised, subtopic-specific statement. */
    private static String localiseBaseHeadline(Locale locale, String base, int productCount,
                                               String market, int year) {
        String clean = base.endsWith(".") ? base.substring(0, base.length() - 1) : base;
        return switch (locale.getLanguage()) {
            case "de" -> productCount + " geprüfte Optionen: " + clean;
            case "fr" -> productCount + " options testées : " + clean;
            default -> productCount + " tested picks: " + clean;
        };
    }

    /** Third line: the concrete next action a real ad would push for. */
    private static String tacticalHook(Locale locale, AngleSpec angle, String base, int productCount,
                                       int lowPrice, String market) {
        return switch (locale.getLanguage()) {
            case "de" -> switch (angle.key()) {
                case "budget" -> "Preisvergleich ab " + lowPrice + " $ — inklusive Folgekosten";
                case "regional" -> "Regionale Preise " + market + " — Stand dieser Woche";
                case "install" -> "Montageaufwand pro Gerät — vor dem Kauf prüfen";
                case "fatigue" -> "Langzeittest: Was nach 90 Tagen noch überzeugt";
                case "mistakes" -> "Die teuersten Fehler beim Kauf vermeiden";
                default -> productCount + " Modelle im Vergleich — Tabelle ansehen";
            };
            case "fr" -> switch (angle.key()) {
                case "budget" -> "Comparatif dès " + lowPrice + " $ — coûts inclus";
                case "regional" -> "Prix constatés en " + market + " cette semaine";
                case "install" -> "Temps et pièces nécessaires avant d'acheter";
                case "fatigue" -> "Après 90 jours : ce qui tient vraiment";
                case "mistakes" -> "Les erreurs qui coûtent le plus cher";
                default -> productCount + " modèles comparés — voir le tableau";
            };
            default -> switch (angle.key()) {
                case "budget" -> "Compare all " + productCount + " picks from $" + lowPrice + ", fees included";
                case "regional" -> market + " prices checked this week — delivered totals";
                case "install" -> "See setup time and extra parts before you buy";
                case "fatigue" -> "90-day check-in: what still performs";
                case "mistakes" -> "Avoid the " + productCount + " most expensive buying mistakes";
                default -> "See all " + productCount + " models ranked side by side";
            };
        };
    }

    private static String pick(List<String> pool, java.util.Random rng) {
        return pool.get(rng.nextInt(pool.size()));
    }

    /**
     * Titles are built per wave so the four articles a publisher runs on one subtopic read like
     * four different pieces of work: the roundup, the price-led shortlist, the head-to-head and
     * the long-term verdict.
     */
    private static String titleFor(Locale locale, int wave, AngleSpec angle, String base,
                                   int productCount, int lowPrice, int highPrice, int months, String market,
                                   int sequence, int style) {
        String subject = subjectOf(base);
        boolean comparison = subject.toLowerCase(java.util.Locale.ROOT).contains(" vs");
        int year = 2026;
        boolean service = style == 1;
        boolean topic = style == 2;

        if (locale.getLanguage().equals("de")) {
            return switch (wave) {
                case 0 -> base;
                case 1 -> service
                        ? "Was %s in %s kostet: echte Preise %d".formatted(subject, market, year)
                        : topic
                                ? "%s in %s richtig wählen: Leitfaden %d".formatted(subject, market, year)
                                : "%s: %d Angebote ab %d $ (%d)".formatted(subject, productCount, lowPrice, year);
                case 2 -> comparison
                        ? "%s: %d Angebote im Preisvergleich".formatted(subject, productCount)
                        : service
                                ? "%s: %d Anbieter im Preis-Leistungs-Vergleich".formatted(subject, productCount)
                                : topic
                                        ? "%s: %d Wege im Vergleich".formatted(subject, productCount)
                                        : "%s im Direktvergleich: %d Modelle".formatted(subject, productCount);
                default -> "%s nach %d Tagen: Was sich bewährt hat".formatted(subject, months);
            };
        }
        if (locale.getLanguage().equals("fr")) {
            return switch (wave) {
                case 0 -> base;
                case 1 -> service
                        ? "%s en %s : les prix réels %d".formatted(subject, market, year)
                        : topic
                                ? "Bien choisir %s en %s : le guide %d".formatted(subject, market, year)
                                : "%s : %d offres dès %d $ (%d)".formatted(subject, productCount, lowPrice, year);
                case 2 -> comparison
                        ? "%s : %d offres comparées".formatted(subject, productCount)
                        : service
                                ? "%s : %d prestataires comparés".formatted(subject, productCount)
                                : topic
                                        ? "%s : %d approches comparées".formatted(subject, productCount)
                                        : "%s face aux alternatives : %d modèles comparés".formatted(subject, productCount);
                default -> "%s après %d jours : ce qui tient vraiment".formatted(subject, months);
            };
        }
        return switch (wave) {
            case 0 -> base;
            case 1 -> service
                    ? premiumPriceTitle(subject, market, year)
                    : topic
                            ? "How to Choose %s in %s (%d)".formatted(subject, market, year)
                            : "%s: %d Picks Under $%d (%d)".formatted(subject, productCount, highPrice, year);
            case 2 -> comparison
                    ? "%s: %d Options Compared".formatted(subject, productCount)
                    : service
                            ? "%s: %d Providers Compared on Price".formatted(subject, productCount)
                            : topic
                                    ? "%s: %d Approaches and What They Cost".formatted(subject, productCount)
                                    : "%s vs. the Alternatives: %d Models Tested".formatted(subject, productCount);
            default -> "%s After %d Days: What Actually Held Up".formatted(subject, months);
        };
    }

    /** Avoids "What X Costs Really Costs": subjects that already talk about money get a price label. */
    private static String premiumPriceTitle(String subject, String market, int year) {
        String lower = subject.toLowerCase(java.util.Locale.ROOT);
        boolean moneyWord = lower.contains("cost") || lower.contains("price") || lower.contains("fee")
                || lower.contains("quote") || lower.contains("rate") || lower.contains("pay");
        // "Denture Alternatives Really Cost" vs "Online Therapy Really Costs".
        String lower2 = subject.toLowerCase(java.util.Locale.ROOT).trim();
        boolean plural = lower2.endsWith("s") && !lower2.endsWith("ss") && !lower2.endsWith("us")
                && !lower2.endsWith("ics");
        String verb = plural ? "Cost" : "Costs";
        return moneyWord
                ? "%s in %s: Real %d Prices".formatted(subject, market, year)
                : "What %s Really %s in %s (%d)".formatted(subject, verb, market, year);
    }

    /**
     * Reduces an editorial headline to a clean noun phrase that still reads well when another
     * template wraps it: "Best Foldable Treadmills for Small Apartments (2026 Tested)" becomes
     * "Foldable Treadmills for Small Apartments".
     */
    private static String subjectOf(String base) {
        if (base == null || base.isBlank()) return "This Category";
        String text = base.replaceAll("\\([^)]*\\)", " ");
        for (String stop : new String[]{":", "\u2014", "\u2013", "?", "|", ","}) {
            int at = text.indexOf(stop);
            if (at > 8) text = text.substring(0, at);
        }
        // "CBT Apps Reviewed Against Real Clinical Frameworks" -> "CBT Apps"
        java.util.regex.Matcher verb = java.util.regex.Pattern
                .compile("(?i)\\s+(reviewed|tested|compared|ranked|explained|measured|benchmarked)\\b")
                .matcher(text);
        if (verb.find() && verb.start() > 10) text = text.substring(0, verb.start());
        text = text.replaceAll("(?i)\\b(top|best)\\s+\\d*\\s*", " ");
        text = text.replaceAll("(?i)\\b(19|20)\\d{2}\\b", " ");
        text = text.replaceAll("\\s+", " ").trim();
        // "Worth Buying in 2026" leaves a dangling preposition once the year is removed.
        text = text.replaceAll("(?i)\\s+\\b(in|for|with|of|at|to|and|or|from|by)\\b\\s*$", "").trim();
        text = text.replaceAll("(?i)\\s+(compared|tested|reviewed|explained|guide|ranking|ranked|report|update)$", "").trim();
        text = text.replaceAll("\\s+", " ").trim();
        String[] words = text.split(" ");
        if (words.length > 9) {
            StringBuilder trimmed = new StringBuilder();
            for (int i = 0; i < 9; i++) {
                if (i > 0) trimmed.append(' ');
                trimmed.append(words[i]);
            }
            text = trimmed.toString();
        }
        if (text.isBlank()) return "This Category";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String headlineFor(Locale locale, AngleSpec angle, String base, int productCount,
                                      int lowPrice, String market, int sequence) {
        // The sequence keeps every campaign's first line unique without exposing internal ids:
        // it only varies numerals, market framing and year, all of which read like real ad copy.
        int year = 2026;
        int band = 40 + Math.floorMod(sequence * 13, 61);
        return switch (locale.getLanguage()) {
            case "de" -> switch (angle.key()) {
                case "budget" -> "Ab " + lowPrice + " $: " + productCount + " Preis-Leistungs-Sieger im Vergleich";
                case "hands-on" -> productCount + " Geräte, " + (20 + sequence % 60) + " Stunden getestet — ehrliche Messwerte";
                case "regional" -> "Preisreport " + market + ": Was Sie " + year + " wirklich zahlen";
                case "fatigue" -> "Nach " + (90 + sequence % 30) + " Tagen: Was noch überzeugt";
                case "mistakes" -> productCount + " teure Kaufirrtümer — und wie Sie sie vermeiden";
                case "install" -> "Montage-Realität: Zusatzteile, Zeitaufwand, Kosten";
                case "side-by-side" -> productCount + " Modelle im Direktvergleich — " + band + " Messpunkte";
                case "checklist" -> "Kaufcheckliste " + year + ": " + productCount + " Punkte vor dem Kauf";
                default -> base.length() > 60 ? base.substring(0, 57) + "..." : base;
            };
            case "fr" -> switch (angle.key()) {
                case "budget" -> "Dès " + lowPrice + " $ : " + productCount + " choix au meilleur rapport qualité-prix";
                case "hands-on" -> productCount + " appareils, " + (20 + sequence % 60) + " heures de test — mesures réelles";
                case "regional" -> "Prix réels en " + market + " : ce que vous payez en " + year;
                case "fatigue" -> "Après " + (90 + sequence % 30) + " jours : ce qui tient encore";
                case "mistakes" -> productCount + " erreurs d'achat coûteuses — et comment les éviter";
                case "install" -> "Installation réelle : pièces, temps, coûts cachés";
                case "side-by-side" -> productCount + " modèles comparés — " + band + " mesures";
                case "checklist" -> "Checklist d'achat " + year + " : " + productCount + " points à vérifier";
                default -> base.length() > 60 ? base.substring(0, 57) + "..." : base;
            };
            default -> switch (angle.key()) {
                case "budget" -> "From $" + lowPrice + ": " + productCount + " Value Picks That Hold Up";
                case "hands-on" -> productCount + " Units, " + (20 + sequence % 60) + " Hours of Testing — Real Measurements";
                case "regional" -> market + " Price Report " + year + ": What You Actually Pay";
                case "fatigue" -> (90 + sequence % 30) + " Days Later: What Still Performs";
                case "mistakes" -> productCount + " Costly Mistakes Buyers Keep Making in " + year;
                case "install" -> "Install Reality Check: Extra Parts and Setup Time";
                case "side-by-side" -> productCount + " Models, " + band + " Data Points Compared";
                case "checklist" -> year + " Buying Checklist: " + productCount + " Checks Before You Pay";
                default -> base.length() > 60 ? base.substring(0, 57) + "..." : base;
            };
        };
    }

    private static String localisedLead(Locale locale, AngleSpec angle, int productCount, int testHours,
                                        int lowPrice, int highPrice, int months, String market) {
        String template = switch (locale.getLanguage()) {
            case "de" -> angle.leadDe();
            case "fr" -> angle.leadFr();
            default -> angle.leadEn();
        };
        return template
                .replace("{n}", Integer.toString(productCount))
                .replace("{hours}", Integer.toString(testHours))
                .replace("{low}", Integer.toString(lowPrice))
                .replace("{high}", Integer.toString(highPrice))
                .replace("{months}", Integer.toString(months))
                .replace("{market}", market);
    }

    /**
     * Applies a native query pattern to an already-native stem exactly once. English keywords get
     * the light yearly/review variation; German and French never mix an English stem with a
     * translated suffix.
     */
    static String keywordVariant(String keyword, String nativeStem, String lang, int slot, int sequence) {
        int bucket = Math.floorMod(sequence + slot, 7);
        if ("de".equals(lang) || "fr".equals(lang)) {
            String stem = nativeStem == null ? "" : nativeStem.trim();
            if (stem.isBlank()) stem = keyword == null ? "" : keyword.trim();
            if (stem.isBlank()) return stem;
            String result = switch (bucket) {
                case 0 -> stem;
                case 1 -> stem + ("de".equals(lang) ? " Test" : " avis");
                case 2 -> stem + ("de".equals(lang) ? " Vergleich" : " comparatif");
                case 3 -> ("fr".equals(lang) ? "prix " : "") + stem + ("de".equals(lang) ? " Preise" : "");
                case 4 -> ("de".equals(lang) ? "beste " : "meilleur ") + stem;
                case 5 -> stem + ("de".equals(lang) ? " Erfahrungen" : " pas cher");
                default -> stem + " 2026";
            };
            return result.length() > 64 ? result.substring(0, 64) : result;
        }
        String english = keyword == null || keyword.isBlank() ? nativeStem : keyword;
        if (english == null || english.isBlank()) return english;
        String result = switch (bucket) {
            case 0 -> english;
            case 1 -> english + " 2026";
            case 2 -> english + " review";
            case 3 -> english + " comparison";
            case 4 -> english + " price";
            case 5 -> "best " + english;
            default -> english + " guide";
        };
        return result.length() > 64 ? result.substring(0, 64) : result;
    }

    /**
     * Builds a native-language search keyword from the localised headline. Only the topic phrase
     * before a separator or preposition is kept, then a native query pattern is applied, so the
     * result reads like an edited German/French SEO target instead of an English query with a
     * translated suffix.
     */
    static String nativeKeyword(String nativeTitle, String lang, int seed) {
        if (nativeTitle == null || nativeTitle.isBlank()) return "";
        String head = nativeTitle.replaceAll("\\([^)]*\\)", " ").replaceAll("(?i)\\b(19|20)\\d{2}\\b", " ");
        head = head.split("[·—:|?,]", 2)[0];
        String[] words = head.trim().split("\\s+");
        StringBuilder core = new StringBuilder();
        for (String word : words) {
            String token = word.replaceAll("\\p{Punct}", "").trim();
            if (token.length() < 2) continue;
            String lower = token.toLowerCase(Locale.ROOT);
            boolean boundary = isSeparator(lower);
            if (boundary && core.length() > 12) break;
            if (boundary) continue;
            if (core.length() > 0 && core.length() + token.length() + 1 > 34) break;
            if (core.length() > 0) core.append(' ');
            core.append(token);
        }
        String stem = core.toString().trim();
        if (stem.isBlank()) return "";
        // Only the topic phrase is returned here; keywordVariant() adds the query modifier once.
        return stem.length() > 48 ? stem.substring(0, 48) : stem;
    }

    private static final java.util.Set<String> STOPWORDS = java.util.Set.of(
            "für", "ohne", "mit", "von", "im", "in", "und", "der", "die", "das", "den", "dem", "des",
            "ein", "eine", "einer", "auf", "bei", "zu", "aus", "am", "an", "ist", "sind",
            "test", "tested", "vergleich", "preise", "kaufen", "kaufberatung", "ratgeber",
            "2019", "2020", "2021", "2022", "2023", "2024", "2025", "2026", "2027",
            "pour", "sans", "avec", "de", "du", "la", "le", "les", "en", "et", "au", "aux",
            "un", "une", "sur", "par", "dans", "chez", "est", "sont",
            "avis", "comparatif", "prix", "tests", "pas", "cher", "achat",
            "for", "with", "without", "the", "and", "to", "best", "top", "review", "reviews");

    private static boolean isSeparator(String token) {
        return STOPWORDS.contains(token);
    }

    private static String nativeQuery(String stem, String lang, int seed) {
        if ("de".equals(lang)) {
            return switch (Math.floorMod(seed, 7)) {
                case 0 -> stem + " 2026";
                case 1 -> stem + " Test";
                case 2 -> stem + " Vergleich";
                case 3 -> stem + " Preise";
                case 4 -> stem + " kaufen";
                case 5 -> stem + " Erfahrungen";
                default -> "beste " + stem;
            };
        }
        return switch (Math.floorMod(seed, 7)) {
            case 0 -> stem + " 2026";
            case 1 -> stem + " avis";
            case 2 -> stem + " comparatif";
            case 3 -> "prix " + stem;
            case 4 -> stem + " pas cher";
            case 5 -> stem + " test";
            default -> "meilleur " + stem;
        };
    }


    /** Keeps the localised stem short enough to leave room for a native modifier. */
    private static String shortKeyword(String keyword) {
        String[] words = keyword.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (out.length() > 0 && out.length() + word.length() + 1 > 34) break;
            if (out.length() > 0) out.append(' ');
            out.append(word);
        }
        return out.length() == 0 ? keyword : out.toString();
    }
}
