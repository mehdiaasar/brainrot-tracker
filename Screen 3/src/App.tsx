import { useEffect } from "react";
import { ChartColumn, House, Settings, Timer } from "lucide-react";

export default function App() {
  return (
    <div>
      <div className="bg-[#faf9f5] text-[#141413] w-full h-fit h-fit min-h-screen w-screen min-w-screen max-w-screen overflow-visible">
        <div className="px-5 pt-8 pb-6">
          <h1 className="font-normal text-[#141413] text-4xl leading-[41px] tracking-[-0.5px]">{`Stats & Reports`}</h1>
          <div className="rounded-2xl bg-[#181715] flex mt-6 p-8 flex-col items-center">
            <div className="relative w-50 h-27.5 overflow-hidden">
              <div className="rounded-t-full bg-[#252320] absolute inset-0" />
              <div className="bg-[conic-gradient(from_270deg_at_50%_100%,#cc785c_0deg,#cc785c_122deg,transparent_122deg)] rounded-t-full absolute inset-0" />
              <div className="rounded-t-full bg-[#181715] absolute inset-x-6 top-6 bottom-0" />
              <div className="flex absolute inset-x-0 bottom-0 pb-1 flex-col justify-end items-center">
                <span className="leading-none font-normal text-[#faf9f5] text-4xl">
                  68
                </span>
              </div>
            </div>
            <div className="text-center mt-3">
              <p className="font-medium uppercase text-[#a09d96] text-xs leading-[17px] tracking-[1.5px]">
                Productivity Score
              </p>
            </div>
          </div>
          <div className="rounded-xl bg-[#efe9de] mt-4 p-6">
            <p className="font-medium text-[#141413] text-base leading-[22px]">
              This Week
            </p>
            <div className="flex mt-3 flex-wrap gap-x-4 gap-y-1">
              <div className="flex items-center gap-1.5">
                <span className="size-2.5 rounded-full bg-[#cc785c]" />
                <span className="font-medium text-[#6c6a64] text-[13px]">
                  TikTok
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="size-2.5 rounded-full bg-[#c64545]" />
                <span className="font-medium text-[#6c6a64] text-[13px]">
                  YouTube
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="size-2.5 rounded-full bg-[#e8a55a]" />
                <span className="font-medium text-[#6c6a64] text-[13px]">
                  Instagram
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="size-2.5 rounded-full bg-[#5db8a6]" />
                <span className="font-medium text-[#6c6a64] text-[13px]">
                  Snapchat
                </span>
              </div>
            </div>
            <div className="flex mt-5 justify-between items-end gap-2 h-37.5">
              <div className="flex flex-col items-center flex-1 gap-2">
                <div className="flex flex-col justify-end w-full h-32.5">
                  <div className="rounded-t-sm bg-[#cc785c] w-full h-7" />
                  <div className="bg-[#c64545] w-full h-5" />
                  <div className="bg-[#e8a55a] w-full h-3.5" />
                  <div className="bg-[#5db8a6] w-full h-2.5" />
                </div>
                <span className="font-medium text-[#6c6a64] text-[13px]">
                  Mon
                </span>
              </div>
              <div className="flex flex-col items-center flex-1 gap-2">
                <div className="flex flex-col justify-end w-full h-32.5">
                  <div className="rounded-t-sm bg-[#cc785c] w-full h-9" />
                  <div className="bg-[#c64545] w-full h-6.5" />
                  <div className="bg-[#e8a55a] w-full h-4.5" />
                  <div className="bg-[#5db8a6] w-full h-3" />
                </div>
                <span className="font-medium text-[#6c6a64] text-[13px]">
                  Tue
                </span>
              </div>
              <div className="flex flex-col items-center flex-1 gap-2">
                <div className="flex flex-col justify-end w-full h-32.5">
                  <div className="rounded-t-sm bg-[#cc785c] w-full h-11" />
                  <div className="bg-[#c64545] w-full h-8.5" />
                  <div className="bg-[#e8a55a] w-full h-6" />
                  <div className="bg-[#5db8a6] w-full h-4.5" />
                </div>
                <span className="font-medium text-[#6c6a64] text-[13px]">
                  Wed
                </span>
              </div>
              <div className="flex flex-col items-center flex-1 gap-2">
                <div className="flex flex-col justify-end w-full h-32.5">
                  <div className="rounded-t-sm bg-[#cc785c] w-full h-7.5" />
                  <div className="bg-[#c64545] w-full h-5.5" />
                  <div className="bg-[#e8a55a] w-full h-4" />
                  <div className="bg-[#5db8a6] w-full h-2.5" />
                </div>
                <span className="font-medium text-[#6c6a64] text-[13px]">
                  Thu
                </span>
              </div>
              <div className="flex flex-col items-center flex-1 gap-2">
                <div className="flex flex-col justify-end w-full h-32.5">
                  <div className="rounded-t-sm bg-[#cc785c] w-full h-10" />
                  <div className="bg-[#c64545] w-full h-7.5" />
                  <div className="bg-[#e8a55a] w-full h-5" />
                  <div className="bg-[#5db8a6] w-full h-3.5" />
                </div>
                <span className="font-medium text-[#6c6a64] text-[13px]">
                  Fri
                </span>
              </div>
              <div className="flex flex-col items-center flex-1 gap-2">
                <div className="flex flex-col justify-end w-full h-32.5">
                  <div className="rounded-t-sm bg-[#cc785c] w-full h-12" />
                  <div className="bg-[#c64545] w-full h-9.5" />
                  <div className="bg-[#e8a55a] w-full h-6.5" />
                  <div className="bg-[#5db8a6] w-full h-4.5" />
                </div>
                <span className="font-medium text-[#6c6a64] text-[13px]">
                  Sat
                </span>
              </div>
              <div className="flex flex-col items-center flex-1 gap-2">
                <div className="flex flex-col justify-end w-full h-32.5">
                  <div className="rounded-t-sm bg-[#cc785c] w-full h-8.5" />
                  <div className="bg-[#c64545] w-full h-6" />
                  <div className="bg-[#e8a55a] w-full h-4" />
                  <div className="bg-[#5db8a6] w-full h-3" />
                </div>
                <span className="font-medium text-[#6c6a64] text-[13px]">
                  Sun
                </span>
              </div>
            </div>
          </div>
          <div className="grid grid-cols-2 mt-4 gap-4">
            <div className="rounded-xl bg-[#efe9de] p-5">
              <p className="font-medium uppercase text-[#6c6a64] text-xs leading-[17px] tracking-[1.5px]">
                Total Videos
              </p>
              <p className="font-normal text-[#141413] text-[28px] leading-[34px] tracking-[-0.3px] mt-2">
                94
              </p>
            </div>
            <div className="rounded-xl bg-[#efe9de] p-5">
              <p className="font-medium uppercase text-[#6c6a64] text-xs leading-[17px] tracking-[1.5px]">
                Total Time
              </p>
              <p className="font-normal text-[#141413] text-[28px] leading-[34px] tracking-[-0.3px] mt-2">
                6h 12m
              </p>
            </div>
          </div>
          <div className="grid grid-cols-2 mt-4 gap-4">
            <div className="rounded-xl bg-[#efe9de] p-4">
              <span className="inline-block font-medium uppercase rounded-full bg-[#5db872] text-white text-xs leading-[17px] tracking-[1.5px] px-3 py-1">
                Best
              </span>
              <p className="font-medium text-[#141413] text-base leading-[22px] mt-3">
                Wednesday
              </p>
              <p className="text-[#6c6a64] text-sm leading-[22px] mt-0.5">
                8 videos
              </p>
            </div>
            <div className="rounded-xl bg-[#efe9de] p-4">
              <span className="inline-block font-medium uppercase rounded-full bg-[#c64545] text-white text-xs leading-[17px] tracking-[1.5px] px-3 py-1">
                Worst
              </span>
              <p className="font-medium text-[#141413] text-base leading-[22px] mt-3">
                Saturday
              </p>
              <p className="text-[#6c6a64] text-sm leading-[22px] mt-0.5">
                22 videos
              </p>
            </div>
          </div>
        </div>
        <div className="sticky bg-[#faf9f5] border-[#e6dfd8] border-t-1 border-r-0 border-b-0 border-l-0 border-solid flex bottom-0 px-5 pt-3 pb-6 justify-around items-center">
          <div className="text-[#6c6a64] flex flex-col items-center gap-1">
            <House className="size-5" />
            <span className="font-medium text-xs">Home</span>
          </div>
          <div className="text-[#6c6a64] flex flex-col items-center gap-1">
            <Timer className="size-5" />
            <span className="font-medium text-xs">Focus</span>
          </div>
          <div className="text-[#cc785c] flex flex-col items-center gap-1">
            <ChartColumn className="size-5" />
            <span className="font-medium text-xs">Stats</span>
          </div>
          <div className="text-[#6c6a64] flex flex-col items-center gap-1">
            <Settings className="size-5" />
            <span className="font-medium text-xs">Settings</span>
          </div>
        </div>
      </div>
    </div>
  );
}
