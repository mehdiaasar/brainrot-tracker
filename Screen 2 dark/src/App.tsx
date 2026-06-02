import { useEffect } from "react";
import {
  Asterisk,
  BarChart3,
  Ghost,
  LayoutDashboard,
  Music2,
  Settings,
  Target,
} from "lucide-react";

import { FallbackComponent } from "./CustomComponents";

export default function App() {
  return (
    <div>
      <div className="bg-neutral-950 text-neutral-50 w-full h-fit h-fit min-h-screen w-screen min-w-screen max-w-screen overflow-visible">
        <div className="px-5 pt-12 pb-8">
          <div className="flex mb-8 justify-between items-center">
            <div className="flex items-center gap-2">
              <Asterisk className="size-5 text-[#faf9f5]" />
              <span className="font-medium text-[#faf9f5] text-base leading-6 tracking-tight">
                FocusCenter
              </span>
            </div>
            <span className="inline-flex font-medium uppercase rounded-full bg-[#cc785c] text-white text-xs leading-4 tracking-[1.5px] px-3 py-1 items-center gap-1.5">
              <span className="size-1.5 rounded-full bg-white" />
              Tracking On
            </span>
          </div>
          <div className="flex mb-6 flex-col items-center">
            <div className="relative size-[180px] rounded-full bg-[#252320] flex justify-center items-center">
              <div className="size-[140px] rounded-full bg-[#181715] flex flex-col justify-center items-center">
                <span className="leading-none font-medium text-[#faf9f5] text-4xl leading-10 tracking-tight">
                  72
                </span>
                <span className="font-medium uppercase text-[#a09d96] text-xs leading-4 tracking-[1.5px] mt-1">
                  Tired
                </span>
              </div>
            </div>
          </div>
          <div className="rounded-xl bg-[#252320] mb-4 p-6">
            <div className="flex items-start gap-3">
              <span className="size-2 shrink-0 rounded-full bg-[#cc785c] mt-2" />
              <p className="italic text-[#a09d96] text-base leading-[25px]">
                You've been mindful today — keep the momentum going.
              </p>
            </div>
          </div>
          <div className="grid grid-cols-2 mb-6 gap-4">
            <div className="rounded-lg bg-[#252320] p-4">
              <div className="leading-none font-medium text-[#faf9f5] text-[28px] tracking-tight">
                1h 24m
              </div>
              <div className="font-medium uppercase text-[#a09d96] text-xs leading-4 tracking-[1.5px] mt-2">
                Time Today
              </div>
            </div>
            <div className="rounded-lg bg-[#252320] p-4">
              <div className="leading-none font-medium text-[#faf9f5] text-[28px] tracking-tight">
                18 videos
              </div>
              <div className="font-medium uppercase text-[#a09d96] text-xs leading-4 tracking-[1.5px] mt-2">
                Reels Watched
              </div>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="rounded-xl bg-[#252320] border-[#2f2c28] border-1 border-solid p-4">
              <div className="flex mb-2 items-center gap-2">
                <FallbackComponent className="size-4 text-[#faf9f5]" />
                <span className="font-medium text-[#faf9f5] text-base leading-6">
                  Instagram
                </span>
              </div>
              <div className="text-[#a09d96] text-sm leading-[21px]">
                12 / 30 videos
              </div>
              <div className="text-[#a09d96] text-sm leading-[21px] mb-3">
                24m / 60m
              </div>
              <div className="rounded-full bg-[#1f1e1b] w-full h-1">
                <div className="w-[40%] rounded-full bg-[#cc785c] h-1" />
              </div>
            </div>
            <div className="rounded-xl bg-[#252320] border-[#2f2c28] border-1 border-solid p-4">
              <div className="flex mb-2 items-center gap-2">
                <FallbackComponent className="size-4 text-[#faf9f5]" />
                <span className="font-medium text-[#faf9f5] text-base leading-6">
                  YouTube
                </span>
              </div>
              <div className="text-[#a09d96] text-sm leading-[21px]">
                8 / 20 videos
              </div>
              <div className="text-[#a09d96] text-sm leading-[21px] mb-3">
                32m / 45m
              </div>
              <div className="rounded-full bg-[#1f1e1b] w-full h-1">
                <div className="w-[70%] rounded-full bg-[#cc785c] h-1" />
              </div>
            </div>
            <div className="rounded-xl bg-[#252320] border-[#2f2c28] border-1 border-solid p-4">
              <div className="flex mb-2 items-center gap-2">
                <Music2 className="size-4 text-[#faf9f5]" />
                <span className="font-medium text-[#faf9f5] text-base leading-6">
                  TikTok
                </span>
              </div>
              <div className="text-[#a09d96] text-sm leading-[21px]">
                15 / 25 videos
              </div>
              <div className="text-[#a09d96] text-sm leading-[21px] mb-3">
                18m / 40m
              </div>
              <div className="rounded-full bg-[#1f1e1b] w-full h-1">
                <div className="w-[60%] rounded-full bg-[#cc785c] h-1" />
              </div>
            </div>
            <div className="rounded-xl bg-[#252320] border-[#2f2c28] border-1 border-solid p-4">
              <div className="flex mb-2 items-center gap-2">
                <Ghost className="size-4 text-[#faf9f5]" />
                <span className="font-medium text-[#faf9f5] text-base leading-6">
                  Snapchat
                </span>
              </div>
              <div className="text-[#a09d96] text-sm leading-[21px]">
                4 / 15 videos
              </div>
              <div className="text-[#a09d96] text-sm leading-[21px] mb-3">
                10m / 30m
              </div>
              <div className="rounded-full bg-[#1f1e1b] w-full h-1">
                <div className="w-[27%] rounded-full bg-[#cc785c] h-1" />
              </div>
            </div>
          </div>
        </div>
        <div className="sticky bg-[#181715] border-[#2f2c28] border-t-1 border-r-0 border-b-0 border-l-0 border-solid bottom-0 px-5 py-3">
          <div className="flex justify-around items-center">
            <div className="text-[#faf9f5] flex flex-col items-center gap-1">
              <LayoutDashboard className="size-5" />
              <span className="font-medium text-[11px]">Dashboard</span>
            </div>
            <div className="text-[#a09d96] flex flex-col items-center gap-1">
              <BarChart3 className="size-5" />
              <span className="font-medium text-[11px]">Insights</span>
            </div>
            <div className="text-[#a09d96] flex flex-col items-center gap-1">
              <Target className="size-5" />
              <span className="font-medium text-[11px]">Goals</span>
            </div>
            <div className="text-[#a09d96] flex flex-col items-center gap-1">
              <Settings className="size-5" />
              <span className="font-medium text-[11px]">Settings</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
