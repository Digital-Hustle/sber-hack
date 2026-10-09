import { Card, PageHeader } from "@/shared/ui";
import { AssistantChat, ChatHistory } from "@/features/ask-assistant";

export const AssistantPage = () => (
  <div className="flex flex-col">
    <PageHeader title="Помощник" sub="Ответы по журналу проходов и графику смен. История сохраняется" className="mb-4 sm:mb-5" />
    <div className="grid gap-4 lg:grid-cols-4">
      <Card className="hidden h-chat flex-col p-3 lg:flex"><ChatHistory className="flex-1" /></Card>
      <Card className="flex h-chat flex-col overflow-hidden p-0 lg:col-span-3"><AssistantChat className="min-h-0 flex-1" /></Card>
    </div>
  </div>
);
