/** Profile image sizes: square avatar, 3:1 banner (like X). Shared with the app. */
export const IMAGE_SIZES = {
  avatar: { width: 400, height: 400 },
  banner: { width: 1500, height: 500 },
} as const;

export const MAX_SOURCE_BYTES = 15 * 1024 * 1024;
const TYPES = ["image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"];

export class ImageError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ImageError";
  }
}

/**
 * Center-crops and scales a picked photo to the target size and re-encodes it as JPEG, so uploads
 * are small and every avatar/banner has the same shape. Browser only.
 */
export async function cropImage(file: File, kind: keyof typeof IMAGE_SIZES): Promise<Blob> {
  if (!TYPES.includes(file.type) && !file.type.startsWith("image/")) throw new ImageError("Choose a JPG, PNG or WebP image.");
  if (file.size > MAX_SOURCE_BYTES) throw new ImageError("That image is too large (15 MB max).");
  const { width, height } = IMAGE_SIZES[kind];
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    throw new ImageError("That image couldn't be read. Try another one.");
  }
  const scale = Math.max(width / bitmap.width, height / bitmap.height);
  const sw = width / scale;
  const sh = height / scale;
  const sx = (bitmap.width - sw) / 2;
  const sy = (bitmap.height - sh) / 2;
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new ImageError("Images aren't supported in this browser.");
  ctx.imageSmoothingQuality = "high";
  ctx.drawImage(bitmap, sx, sy, sw, sh, 0, 0, width, height);
  bitmap.close();
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.86));
  if (!blob) throw new ImageError("That image couldn't be processed.");
  return blob;
}
