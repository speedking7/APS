import { readText } from "./fs";

export function getFrontmatterMetadata(filePath: string): Record<string, string> {
  const lines = readText(filePath).split(/\r?\n/u);
  if (lines.length < 3) {
    return {};
  }

  let startIndex = -1;
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index]!.trim();
    if (line === "---") {
      startIndex = index;
      break;
    }

    if (line.length > 0 && !line.startsWith("#")) {
      break;
    }
  }

  if (startIndex < 0 || startIndex >= lines.length - 1) {
    return {};
  }

  const metadata: Record<string, string> = {};
  for (let index = startIndex + 1; index < lines.length; index += 1) {
    const line = lines[index]!;
    if (line.trim() === "---") {
      break;
    }
    const match = /^\s*([^:]+):\s*(.*)$/u.exec(line);
    if (match) {
      metadata[match[1]!.trim()] = match[2]!.trim().replace(/^"(.*)"$/u, "$1");
    }
  }

  return metadata;
}
