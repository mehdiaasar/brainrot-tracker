import { useEffect } from "react";
import { Asterisk, Settings } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";

export default function App() {
  return (
    <div>
      <div className="min-h-[874px] bg-[#faf9f5] text-[#141413] flex px-8 pt-24 pb-6 flex-col w-full h-fit h-fit min-h-screen w-screen min-w-screen max-w-screen overflow-visible">
        <div className="flex flex-col items-center gap-6">
          <div className="flex items-center gap-2">
            <Asterisk className="size-6 text-[#141413]" strokeWidth={2.5} />
            <span className="font-serif text-[#141413] text-[28px] leading-[34px] tracking-[-0.3px]">
              FocusFlow
            </span>
          </div>
          <div className="flex flex-col items-center gap-2 w-full">
            <div className="max-w-[240px] flex items-center gap-2 w-full">
              <div className="rounded-full bg-[#cc785c] flex-1 h-1.5" />
              <div className="rounded-full bg-[#e6dfd8] flex-1 h-1.5" />
              <div className="rounded-full bg-[#e6dfd8] flex-1 h-1.5" />
              <div className="rounded-full bg-[#e6dfd8] flex-1 h-1.5" />
            </div>
            <span className="font-medium uppercase text-[#6c6a64] text-xs tracking-[1.5px]">
              Step 1 of 4
            </span>
          </div>
        </div>
        <h1 className="font-serif text-[#141413] text-4xl leading-[41px] tracking-[-0.5px] mt-8">
          Grant Permissions
        </h1>
        <div className="flex mt-6 flex-col gap-2">
          <Card className="shadow-none rounded-xl bg-[#efe9de] border-[#e6dfd8] border-1 border-solid p-8 gap-4">
            <CardHeader className="p-0 gap-2">
              <div className="flex justify-between items-center gap-2">
                <span className="font-medium text-[#141413] text-base leading-[22px]">
                  Accessibility Access
                </span>
                <span className="shrink-0 font-medium uppercase rounded-full bg-[#5db872] text-white text-xs tracking-[1.5px] px-3 py-1">
                  Granted
                </span>
              </div>
              <p className="text-[#6c6a64] text-sm leading-[22px]">
                Allows FocusFlow to detect active apps and screen usage.
              </p>
            </CardHeader>
            <CardContent className="p-0 gap-0">
              <Button className="font-medium rounded-lg bg-[#faf9f5] text-[#141413] text-sm border-[#e6dfd8] border-1 border-solid w-full h-10">
                <Settings className="size-4 mr-2" />
                Open Settings
              </Button>
            </CardContent>
          </Card>
          <Card className="shadow-none rounded-xl bg-[#efe9de] border-[#e6dfd8] border-1 border-solid p-8 gap-4">
            <CardHeader className="p-0 gap-2">
              <div className="flex justify-between items-center gap-2">
                <span className="font-medium text-[#141413] text-base leading-[22px]">
                  Overlay Permission
                </span>
                <span className="shrink-0 font-medium uppercase rounded-full bg-[#efe9de] text-[#141413] text-xs tracking-[1.5px] border-[#e6dfd8] border-1 border-solid px-3 py-1">
                  Pending
                </span>
              </div>
              <p className="text-[#6c6a64] text-sm leading-[22px]">
                Lets FocusFlow show focus reminders over other apps.
              </p>
            </CardHeader>
            <CardContent className="p-0 gap-0">
              <Button className="font-medium rounded-lg bg-[#faf9f5] text-[#141413] text-sm border-[#e6dfd8] border-1 border-solid w-full h-10">
                <Settings className="size-4 mr-2" />
                Open Settings
              </Button>
            </CardContent>
          </Card>
          <Card className="shadow-none rounded-xl bg-[#efe9de] border-[#e6dfd8] border-1 border-solid p-8 gap-4">
            <CardHeader className="p-0 gap-2">
              <div className="flex justify-between items-center gap-2">
                <span className="font-medium text-[#141413] text-base leading-[22px]">
                  Notification Access
                </span>
                <span className="shrink-0 font-medium uppercase rounded-full bg-[#efe9de] text-[#141413] text-xs tracking-[1.5px] border-[#e6dfd8] border-1 border-solid px-3 py-1">
                  Pending
                </span>
              </div>
              <p className="text-[#6c6a64] text-sm leading-[22px]">
                Enables daily focus summaries and gentle nudges.
              </p>
            </CardHeader>
            <CardContent className="p-0 gap-0">
              <Button className="font-medium rounded-lg bg-[#faf9f5] text-[#141413] text-sm border-[#e6dfd8] border-1 border-solid w-full h-10">
                <Settings className="size-4 mr-2" />
                Open Settings
              </Button>
            </CardContent>
          </Card>
        </div>
        <div className="flex mt-auto pt-8 flex-col items-center gap-4">
          <Button className="font-medium rounded-lg bg-[#cc785c] text-white text-sm w-full h-10">
            Continue
          </Button>
          <button className="bg-transparent font-medium text-[#6c6a64] text-sm">
            Skip Setup
          </button>
        </div>
      </div>
    </div>
  );
}
