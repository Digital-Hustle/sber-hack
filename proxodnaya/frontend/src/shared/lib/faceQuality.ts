// ADR-047: проверка снимка-эталона сразу при оформлении. В песочнице нет биометрии — оцениваем сам снимок
// (свет, контраст, резкость, размер) и ищем повтор по перцептивному хэшу центра кадра. В продукте то же
// делает сервис face: YuNet (ровно одно лицо, размер, поворот) + SFace (поиск повтора 1:N по эталонам).

export type FaceQuality = {
  /** 0..1 — средняя яркость центра кадра. */
  brightness: number;
  /** 0..1 — разброс яркости (однотонный кадр — лица нет). */
  contrast: number;
  /** Дисперсия лапласиана — чем меньше, тем сильнее смазан снимок. */
  sharpness: number;
  /** Меньшая сторона исходного снимка, px. */
  side: number;
  /** 64-битный хэш центра кадра (16 hex) — для поиска повторов. */
  hash: string;
};

const N = 64;

const loadImage = (src: string) => new Promise<HTMLImageElement>((resolve, reject) => {
  const img = new Image();
  img.onload = () => resolve(img);
  img.onerror = () => reject(new Error("Не удалось прочитать снимок"));
  img.src = src;
});

/** Серый центр кадра N×N: овал лица в рамке занимает середину, фон по краям почти не влияет. */
const grayCenter = (img: HTMLImageElement, size: number) => {
  const c = document.createElement("canvas");
  c.width = size; c.height = size;
  const ctx = c.getContext("2d", { willReadFrequently: true })!;
  const side = Math.min(img.width, img.height);
  const crop = side * 0.7;
  ctx.drawImage(img, (img.width - crop) / 2, (img.height - crop) / 2, crop, crop, 0, 0, size, size);
  const px = ctx.getImageData(0, 0, size, size).data;
  const g = new Float32Array(size * size);
  for (let i = 0; i < g.length; i++) g[i] = (0.299 * px[i * 4] + 0.587 * px[i * 4 + 1] + 0.114 * px[i * 4 + 2]) / 255;
  return g;
};

/** Разностный хэш (dHash) 8×8: устойчив к яркости и сжатию, меняется при другом лице/ракурсе. */
const dHash = (img: HTMLImageElement) => {
  const c = document.createElement("canvas");
  c.width = 9; c.height = 8;
  const ctx = c.getContext("2d", { willReadFrequently: true })!;
  const side = Math.min(img.width, img.height);
  const crop = side * 0.6;
  ctx.drawImage(img, (img.width - crop) / 2, (img.height - crop) / 2, crop, crop, 0, 0, 9, 8);
  const px = ctx.getImageData(0, 0, 9, 8).data;
  const lum = (x: number, y: number) => { const i = (y * 9 + x) * 4; return px[i] * 0.299 + px[i + 1] * 0.587 + px[i + 2] * 0.114; };
  let bits = "";
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) bits += lum(x, y) > lum(x + 1, y) ? "1" : "0";
  let hex = "";
  for (let i = 0; i < 64; i += 4) hex += parseInt(bits.slice(i, i + 4), 2).toString(16);
  return hex;
};

export const analyzeFace = async (dataUrl: string): Promise<FaceQuality> => {
  const img = await loadImage(dataUrl);
  const g = grayCenter(img, N);
  let sum = 0;
  for (const v of g) sum += v;
  const mean = sum / g.length;
  let varSum = 0;
  for (const v of g) varSum += (v - mean) ** 2;
  // Лапласиан 3×3 по внутренним точкам.
  const lap: number[] = [];
  for (let y = 1; y < N - 1; y++) for (let x = 1; x < N - 1; x++) {
    const i = y * N + x;
    lap.push(g[i - N] + g[i + N] + g[i - 1] + g[i + 1] - 4 * g[i]);
  }
  const lm = lap.reduce((s, v) => s + v, 0) / lap.length;
  const sharp = lap.reduce((s, v) => s + (v - lm) ** 2, 0) / lap.length;
  return { brightness: mean, contrast: Math.sqrt(varSum / g.length), sharpness: sharp, side: Math.min(img.width, img.height), hash: dHash(img) };
};

/** Число различающихся бит двух хэшей. */
export const hashDistance = (a: string, b: string) => {
  let d = 0;
  for (let i = 0; i < Math.min(a.length, b.length); i++) {
    let x = parseInt(a[i], 16) ^ parseInt(b[i], 16);
    while (x) { d += x & 1; x >>= 1; }
  }
  return d + Math.abs(a.length - b.length) * 4;
};
