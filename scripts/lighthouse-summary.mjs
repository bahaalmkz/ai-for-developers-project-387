// Сводка отчёта Lighthouse CI для агента аудита (воркфлоу lighthouse-audit.yml).
// Читает manifest.json и JSON-отчёты из `lhci upload --target=filesystem`
// и пишет summary.json (данные для агента) и summary.md (для человека и summary прогона).
//
// Запуск: node scripts/lighthouse-summary.mjs <каталог .audit>
// Переменные окружения:
//   APP_URL        — адрес проверенного приложения;
//   AUDIT_STATUS   — ok | no_data (приложение недоступно, отчёта нет);
//   TRAINING       — true: добавить учебную находку (явно помеченную, не дефект приложения);
//   RUN_URL        — ссылка на прогон Actions.
//
// Находка — проверка Lighthouse с весом в оценке категории и score < 0.5 (провал).
// Ключ находки `lighthouse:<id проверки>` не зависит от страницы и значений:
// по нему агент ищет уже заведённый issue и не создаёт дубль.

import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const auditDir = process.argv[2] ?? '.audit';
const appUrl = process.env.APP_URL ?? '';
const status = process.env.AUDIT_STATUS === 'ok' ? 'ok' : 'no_data';
const training = process.env.TRAINING === 'true';
const runUrl = process.env.RUN_URL ?? '';

const CATEGORIES = ['performance', 'accessibility', 'best-practices', 'seo'];
const FAIL_SCORE = 0.5;

const pages = [];
const findingsByKey = new Map();

const manifestPath = join(auditDir, 'lhci', 'manifest.json');
if (status === 'ok' && existsSync(manifestPath)) {
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  for (const entry of manifest.filter((e) => e.isRepresentativeRun)) {
    const lhr = JSON.parse(readFileSync(entry.jsonPath, 'utf8'));
    const path = new URL(lhr.finalDisplayedUrl ?? lhr.finalUrl ?? entry.url).pathname;
    const scores = {};
    for (const id of CATEGORIES) {
      const category = lhr.categories[id];
      if (!category) continue;
      scores[id] = category.score;
      for (const ref of category.auditRefs) {
        const audit = lhr.audits[ref.id];
        if (!audit || ref.weight <= 0 || audit.score === null || audit.score >= FAIL_SCORE) continue;
        const key = `lighthouse:${ref.id}`;
        const finding = findingsByKey.get(key) ?? {
          key,
          training: false,
          auditId: ref.id,
          category: id,
          weight: ref.weight,
          title: audit.title,
          description: audit.description,
          pages: [],
        };
        finding.weight = Math.max(finding.weight, ref.weight);
        finding.pages.push({ path, score: audit.score, displayValue: audit.displayValue ?? '' });
        findingsByKey.set(key, finding);
      }
    }
    pages.push({ url: entry.url, path, scores, report: entry.htmlPath.replace(/^.*[\\/]/, '') });
  }
}

const findings = [...findingsByKey.values()].sort((a, b) => b.weight - a.weight);

if (training) {
  // Учебная находка для демонстрации сценария «новая находка → issue».
  // Это не результат проверки приложения: в issue она помечается как учебная.
  findings.unshift({
    key: 'training:lighthouse-demo-render-blocking',
    training: true,
    auditId: 'render-blocking-resources',
    category: 'performance',
    weight: 0,
    title: 'УЧЕБНАЯ находка: ресурсы, блокирующие отрисовку страницы /book',
    description:
      'Подготовленная учебная находка для проверки сценария аудита. Не получена из отчёта Lighthouse ' +
      'и не является подтверждённым дефектом приложения.',
    pages: [{ path: '/book', score: 0.3, displayValue: 'Потенциальная экономия 600 мс (учебное значение)' }],
  });
}

const summary = {
  appUrl,
  runUrl,
  status,
  training,
  generatedAt: new Date().toISOString(),
  pages,
  findings,
};
writeFileSync(join(auditDir, 'summary.json'), JSON.stringify(summary, null, 2) + '\n');

const pct = (score) => (score === null || score === undefined ? '—' : String(Math.round(score * 100)));
const lines = [`## Аудит Lighthouse: ${appUrl}`, ''];
if (status !== 'ok') {
  lines.push('**Нет данных:** приложение не ответило, отчёт Lighthouse не собран. Это не значит, что проблем нет.');
} else {
  lines.push('| Страница | Производительность | Доступность | Практики | SEO |', '| --- | --- | --- | --- | --- |');
  for (const page of pages) {
    const s = page.scores;
    lines.push(
      `| \`${page.path}\` | ${pct(s.performance)} | ${pct(s.accessibility)} | ${pct(s['best-practices'])} | ${pct(s.seo)} |`,
    );
  }
}
lines.push('', `Находок (провал проверки с весом в оценке, score < ${FAIL_SCORE}): ${findings.length}`);
for (const f of findings) {
  const where = f.pages.map((p) => `\`${p.path}\` ${pct(p.score)}${p.displayValue ? ` (${p.displayValue})` : ''}`);
  lines.push(`- \`${f.key}\`${f.training ? ' **[учебная]**' : ''} — ${f.title}: ${where.join(', ')}`);
}
if (runUrl) lines.push('', `Прогон: ${runUrl}`);
writeFileSync(join(auditDir, 'summary.md'), lines.join('\n') + '\n');

console.log(lines.join('\n'));
