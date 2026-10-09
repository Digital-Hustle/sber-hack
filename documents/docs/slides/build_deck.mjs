// Слайды защиты «Документы» (X-06). Источник правды — этот файл: node build_deck.mjs
// Числа — из docs/MATH.md, EVAL.md и CI; ничего не придумано (замеры моделей — после открытого набора).
import { buildPptx, createDeck } from "/skills/pptx/scripts/pptx_builder_runtime.mjs"
import { applyRecipeDeckPlan } from "/skills/pptx/scripts/slide_recipes.mjs"

const deck = createDeck({ width: 1280, height: 720 })

applyRecipeDeckPlan(deck, [
	{
		recipeId: "cover-editorial",
		content: {
			eyebrow: "Кейс «Документы» · Сбер Хакатон",
			title: "Документы соискателя: от фото до проверенных полей",
			subtitle: "Соискатель снимает документы телефоном, система сама их читает и проверяет, оператор смотрит только сомнительное.",
			section: "01",
		},
	},
	{
		recipeId: "big-claim",
		content: {
			eyebrow: "Проблема",
			claim: "Документы шлют фото в чат и перепечатывают руками",
			support: "Плохой снимок выясняется через дни.",
		},
	},
	{
		recipeId: "process-steps",
		content: {
			eyebrow: "Конвейер",
			title: "Файл принят сразу, разбор идёт сам",
			steps: [
				{ title: "Приём", body: "Ответ сразу, файл зашифрован" },
				{ title: "Разбор", body: "Текст, вид, поля — сами" },
				{ title: "Проверки", body: "Суммы, MRZ, сроки — кодом" },
			],
		},
	},
	{
		recipeId: "image-left-story",
		content: {
			eyebrow: "Модель извлекает, код проверяет",
			title: "Модели одной не верим",
			body: "Номер сверяется с MRZ, контрольные цифры считает код. Значение, которого нет на скане, получает штраф.",
			image: { src: new URL("../../eval/synthetic/syn-001.jpg", import.meta.url).pathname, fit: "contain", alt: "Синтетический паспорт из набора eval" },
			source: "Синтетический документ из eval/synthetic, реальных ПДн в репозитории нет",
		},
	},
	{
		recipeId: "table-led",
		content: {
			eyebrow: "Матмодель · уверенность поля",
			title: "c = 0,4·модель + 0,3·OCR + 0,3·проверки",
			takeaway: "Порог τ = 0,8: всё, что ниже, или с проваленной проверкой, — наверх к оператору.",
			table: {
				rows: [
					["Поле", "Модель", "OCR", "Проверки", "c", "Итог"],
					["СНИЛС, сумма сошлась", "0,90", "0,85", "1", "0,915", "автоматически"],
					["СНИЛС, сумма не сошлась", "0,90", "0,85", "0", "0,615", "оператору"],
					["ФИО, без проверок", "0,90", "0,85", "0,7", "0,825", "автоматически"],
					["ФИО, плохо распознано", "0,90", "0,40", "0,7", "0,690", "оператору"],
				],
				columnWidths: [330, 140, 140, 160, 140, 226],
			},
		},
	},
	{
		recipeId: "chart-led",
		content: {
			eyebrow: "Матмодель · очередь M/M/c",
			title: "Два слота модели держат 4 документа в минуту",
			takeaway: "Эрланг C, μ = 3 док/мин. Синие — 2 слота, серые — 3.",
			chart: {
				chartType: "column",
				categories: ["1 док/мин", "2,5 док/мин", "4 док/мин", "5,5 док/мин"],
				series: [
					{ name: "2 слота модели", values: [1, 4, 16, 105], unit: "с" },
					{ name: "3 слота модели", values: [0, 1, 2, 6], unit: "с" },
				],
				valueAxisTitle: "Ожидание, с",
				showValueAxis: true,
			},
		},
	},
	{
		recipeId: "evidence-panel",
		content: {
			eyebrow: "Устойчивость",
			title: "Рестарт и падение модели не страшны",
			claim: "Задания не теряются.",
			evidence: [
				["Рестарт docproc", "задание доделано"],
				["Модель упала", "ждём, затем догоняем"],
				["Повтор события", "одно задание"],
				["Перегрузка", "семафор и выключатель"],
			],
		},
	},
	{
		recipeId: "table-led",
		content: {
			eyebrow: "Безопасность и ПДн",
			title: "«Злоумышленник» запускается в CI, а не раз перед защитой",
			takeaway: "scripts/attack.sh гоняет чек-лист на поднятом стенде при каждом изменении инфраструктуры.",
			table: {
				rows: [
					["Атака", "Ответ системы"],
					["Ссылка на карточку или скан без входа", "401, сервис сам проверяет JWT и роль"],
					["Чужой ключ заявки", "404, ключ привязан к одной заявке"],
					["Токен alg=none, чужой issuer или aud", "401"],
					["Внутренние ручки снаружи", "404 до проверки токена"],
					["Подбор кода возврата", "429 и Retry-After"],
				],
				columnWidths: [520, 616],
			},
		},
	},
	{
		recipeId: "metric-row",
		content: {
			eyebrow: "Инженерия",
			title: "Проверяется автоматически на каждом PR",
			metrics: [
				{ value: "87", label: "тестов бэкенда" },
				{ value: "28", label: "синтетических документа" },
				{ value: "56", label: "кодов сверено с API.md" },
				{ value: "1", label: "команда для стенда" },
			],
		},
	},
	{
		recipeId: "closing-takeaways",
		content: {
			eyebrow: "Итог",
			title: "Что важно запомнить",
			takeaways: [
				"О плохом снимке соискатель узнаёт сразу.",
				"Оператор смотрит только сомнительные поля.",
				"Модель локальная или облачная — одной настройкой.",
				"Веса экспертные; калибровка — на открытом наборе.",
			],
		},
	},
])

await buildPptx(deck, {
	scenePath: "build/deck.scene.json",
	outputPath: "documents-defense.pptx",
	reportPath: "build/deck.build-report.json",
	previewDir: "build/preview",
	layoutDir: "build/layout",
})
