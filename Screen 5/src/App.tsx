import { useEffect } from "react";
import { ChartColumn, Flame, House, Lock, Settings } from "lucide-react";

export default function App() {
  return (
    <div>
      <div className="bg-[#faf9f5] text-[#141413] pb-12 w-full h-fit h-fit min-h-screen w-screen min-w-screen max-w-screen overflow-visible">
        <div className="px-5 pt-8 pb-6">
          <h1 className="font-serif text-[#141413] text-4xl leading-[41px] tracking-[-0.5px]">{`Streaks & Progress`}</h1>
        </div>
        <div className="px-5">
          <div className="rounded-2xl bg-[#181715] flex p-8 flex-col items-center w-full">
            <span className="leading-none text-[32px]">🔥</span>
            <span className="font-serif text-[#faf9f5] text-[64px] leading-[67px] tracking-[-1.5px] mt-4">
              12
            </span>
            <span className="font-medium text-[#a09d96] text-xs tracking-[1.5px] mt-2">
              DAY STREAK
            </span>
            <div className="flex mt-8 justify-center items-center gap-6 w-full">
              <div className="text-center flex-1">
                <p className="text-[#a09d96] text-sm leading-[22px]">Current</p>
                <p className="text-[#faf9f5] text-sm leading-[22px]">12 days</p>
              </div>
              <div className="bg-[#252320] self-stretch w-px" />
              <div className="text-center flex-1">
                <p className="text-[#a09d96] text-sm leading-[22px]">Record</p>
                <p className="text-[#faf9f5] text-sm leading-[22px]">21 days</p>
              </div>
            </div>
          </div>
        </div>
        <div className="mt-6 px-5">
          <div className="rounded-xl bg-[#efe9de] p-6 w-full">
            <div className="grid grid-cols-7 mb-3 gap-1">
              <span className="font-medium text-center text-[#6c6a64] text-xs tracking-[1.5px]">
                M
              </span>
              <span className="font-medium text-center text-[#6c6a64] text-xs tracking-[1.5px]">
                T
              </span>
              <span className="font-medium text-center text-[#6c6a64] text-xs tracking-[1.5px]">
                W
              </span>
              <span className="font-medium text-center text-[#6c6a64] text-xs tracking-[1.5px]">
                T
              </span>
              <span className="font-medium text-center text-[#6c6a64] text-xs tracking-[1.5px]">
                F
              </span>
              <span className="font-medium text-center text-[#6c6a64] text-xs tracking-[1.5px]">
                S
              </span>
              <span className="font-medium text-center text-[#6c6a64] text-xs tracking-[1.5px]">
                S
              </span>
            </div>
            <div className="grid grid-cols-7 gap-1">
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#5db872] text-white text-[13px] flex justify-center items-center">
                  1
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#5db872] text-white text-[13px] flex justify-center items-center">
                  2
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#c64545] text-white text-[13px] flex justify-center items-center">
                  3
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#5db872] text-white text-[13px] flex justify-center items-center">
                  4
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#5db872] text-white text-[13px] flex justify-center items-center">
                  5
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#5db872] text-white text-[13px] flex justify-center items-center">
                  6
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#5db872] text-white text-[13px] flex justify-center items-center">
                  7
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#5db872] text-white text-[13px] flex justify-center items-center">
                  8
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#c64545] text-white text-[13px] flex justify-center items-center">
                  9
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#5db872] text-white text-[13px] flex justify-center items-center">
                  10
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#5db872] text-white text-[13px] flex justify-center items-center">
                  11
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 ring-2 ring-[#cc785c] font-medium rounded-full bg-[#5db872] text-white text-[13px] flex justify-center items-center">
                  12
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#ebe6df] text-[#6c6a64] text-[13px] flex justify-center items-center">
                  13
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#ebe6df] text-[#6c6a64] text-[13px] flex justify-center items-center">
                  14
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#ebe6df] text-[#6c6a64] text-[13px] flex justify-center items-center">
                  15
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#ebe6df] text-[#6c6a64] text-[13px] flex justify-center items-center">
                  16
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#ebe6df] text-[#6c6a64] text-[13px] flex justify-center items-center">
                  17
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#ebe6df] text-[#6c6a64] text-[13px] flex justify-center items-center">
                  18
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#ebe6df] text-[#6c6a64] text-[13px] flex justify-center items-center">
                  19
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#ebe6df] text-[#6c6a64] text-[13px] flex justify-center items-center">
                  20
                </div>
              </div>
              <div className="flex justify-center">
                <div className="size-9 font-medium rounded-full bg-[#ebe6df] text-[#6c6a64] text-[13px] flex justify-center items-center">
                  21
                </div>
              </div>
            </div>
          </div>
        </div>
        <div className="mt-8 mb-4 px-5">
          <h2 className="font-medium text-[#141413] text-lg leading-[25px]">
            Achievements
          </h2>
        </div>
        <div className="flex px-5 flex-col gap-2">
          <div className="rounded-xl bg-[#efe9de] border-[#e6dfd8] border-1 border-solid flex p-4 items-center gap-4 w-full">
            <div className="size-10 shrink-0 rounded-full bg-[#e8e0d2] text-xl flex justify-center items-center">
              🌱
            </div>
            <div className="min-w-0 flex-1">
              <p className="font-medium text-[#141413] text-base leading-[22px]">
                First Step
              </p>
              <p className="text-[#6c6a64] text-sm leading-[22px]">
                1 day under limit
              </p>
            </div>
            <div className="shrink-0 rounded-full bg-[#5db872] px-3 py-1">
              <span className="font-medium text-white text-xs tracking-[1.5px]">
                DONE
              </span>
            </div>
          </div>
          <div className="rounded-xl bg-[#efe9de] border-[#e6dfd8] border-1 border-solid flex p-4 items-center gap-4 w-full">
            <div className="size-10 shrink-0 rounded-full bg-[#e8e0d2] text-xl flex justify-center items-center">
              ⚔️
            </div>
            <div className="min-w-0 flex-1">
              <p className="font-medium text-[#141413] text-base leading-[22px]">
                7-Day Warrior
              </p>
              <p className="text-[#6c6a64] text-sm leading-[22px]">
                7 consecutive days
              </p>
            </div>
            <div className="shrink-0 rounded-full bg-[#5db872] px-3 py-1">
              <span className="font-medium text-white text-xs tracking-[1.5px]">
                DONE
              </span>
            </div>
          </div>
          <div className="rounded-xl bg-[#efe9de] border-[#e6dfd8] border-1 border-solid flex p-4 items-center gap-4 w-full">
            <div className="size-10 shrink-0 rounded-full bg-[#e8e0d2] text-xl flex justify-center items-center">
              🏆
            </div>
            <div className="min-w-0 flex-1">
              <p className="font-medium text-[#141413] text-base leading-[22px]">
                30-Day Legend
              </p>
              <p className="text-[#6c6a64] text-sm leading-[22px]">
                30 consecutive days
              </p>
            </div>
            <div className="shrink-0 rounded-full bg-[#e6dfd8] flex px-3 py-1.5 justify-center items-center">
              <Lock className="size-3.5 text-[#6c6a64]" />
            </div>
          </div>
          <div className="rounded-xl bg-[#efe9de] border-[#e6dfd8] border-1 border-solid flex p-4 items-center gap-4 w-full">
            <div className="size-10 shrink-0 rounded-full bg-[#e8e0d2] text-xl flex justify-center items-center">
              🚫
            </div>
            <div className="min-w-0 flex-1">
              <p className="font-medium text-[#141413] text-base leading-[22px]">
                Zero Reel Day
              </p>
              <p className="text-[#6c6a64] text-sm leading-[22px]">
                0 videos watched
              </p>
            </div>
            <div className="shrink-0 rounded-full bg-[#5db872] px-3 py-1">
              <span className="font-medium text-white text-xs tracking-[1.5px]">
                DONE
              </span>
            </div>
          </div>
          <div className="rounded-xl bg-[#efe9de] border-[#e6dfd8] border-1 border-solid flex p-4 items-center gap-4 w-full">
            <div className="size-10 shrink-0 rounded-full bg-[#e8e0d2] text-xl flex justify-center items-center">
              ⚡
            </div>
            <div className="min-w-0 flex-1">
              <p className="font-medium text-[#141413] text-base leading-[22px]">
                Half Hour Hero
              </p>
              <p className="text-[#6c6a64] text-sm leading-[22px]">
                Under 30 min total
              </p>
            </div>
            <div className="shrink-0 rounded-full bg-[#e6dfd8] flex px-3 py-1.5 justify-center items-center">
              <Lock className="size-3.5 text-[#6c6a64]" />
            </div>
          </div>
        </div>
        <div className="mt-10 px-5">
          <div className="border-[#e6dfd8] border-t-1 border-r-0 border-b-0 border-l-0 border-solid flex pt-3 justify-around items-center w-full">
            <div className="flex flex-col items-center gap-1">
              <House className="size-5 text-[#6c6a64]" />
              <span className="font-medium text-[#6c6a64] text-xs tracking-[1.5px]">
                HOME
              </span>
            </div>
            <div className="flex flex-col items-center gap-1">
              <ChartColumn className="size-5 text-[#6c6a64]" />
              <span className="font-medium text-[#6c6a64] text-xs tracking-[1.5px]">
                STATS
              </span>
            </div>
            <div className="flex flex-col items-center gap-1">
              <Flame className="size-5 text-[#cc785c]" />
              <span className="font-medium text-[#cc785c] text-xs tracking-[1.5px]">
                STREAKS
              </span>
            </div>
            <div className="flex flex-col items-center gap-1">
              <Settings className="size-5 text-[#6c6a64]" />
              <span className="font-medium text-[#6c6a64] text-xs tracking-[1.5px]">
                SETTINGS
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
