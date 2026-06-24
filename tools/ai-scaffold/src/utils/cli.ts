export type CommandContext = {
  repoRoot: string;
};

export type CommandHandler = (args: string[], context: CommandContext) => Promise<void>;

export function takeFlag(args: string[], name: string): boolean {
  const index = args.indexOf(name);
  if (index < 0) {
    return false;
  }

  args.splice(index, 1);
  return true;
}

export function takeOption(args: string[], name: string): string | undefined {
  const index = args.indexOf(name);
  if (index < 0) {
    return undefined;
  }

  const value = args[index + 1];
  if (!value) {
    throw new Error(`Missing value for ${name}`);
  }

  args.splice(index, 2);
  return value;
}

export function requireOption(args: string[], name: string): string {
  const value = takeOption(args, name);
  if (!value) {
    throw new Error(`Missing required option ${name}`);
  }

  return value;
}

export function assertNoExtraArgs(args: string[]): void {
  if (args.length > 0) {
    throw new Error(`Unexpected arguments: ${args.join(" ")}`);
  }
}
