import { useEffect } from "react";
import { Asterisk, Check, Ghost, Music2 } from "lucide-react";

import { FallbackComponent } from "./CustomComponents";

export default function App() {
  return (
    <div>
      <div className="font-['StyreneB','Inter',sans-serif] bg-[#181715] text-[#faf9f5] w-full h-fit h-fit min-h-screen w-screen min-w-screen max-w-screen overflow-visible">
        <div className="px-5 pt-8 pb-3">
          <div className="flex items-center gap-2">
            <Asterisk className="size-6 text-[#faf9f5]" />
            <span className="font-medium uppercase text-[#a09d96] text-[13px] tracking-[1.5px]">
              Settings
            </span>
          </div>
          <h1 className="font-['Copernicus','Tiempos_Headline',serif] font-normal text-[#faf9f5] text-4xl leading-[41px] tracking-[-0.5px] mt-3" />
        </div>
        <div className="px-5 pb-2">
          <div className="rounded-xl bg-[#1f1e1b] border-[#2f2c28] border-1 border-solid flex p-6 justify-between items-center gap-4 w-full">
            <div className="flex flex-col gap-1">
              <span className="font-medium text-[#faf9f5] text-base leading-[22px]">
                Strict Mode
              </span>
              <span className="font-normal text-[#a09d96] text-sm leading-5">
                Locks limits once daily cap is hit
              </span>
            </div>
            <div className="shrink-0 rounded-full bg-[#cc785c] flex p-1 justify-end items-center w-12 h-7">
              <div className="size-5 rounded-full bg-white" />
            </div>
          </div>
        </div>
        <div className="flex px-5 pt-3 pb-28 flex-col gap-3">
          <div className="rounded-xl bg-[#252320] border-[#2f2c28] border-1 border-solid flex p-6 flex-col gap-4 w-full">
            <div className="flex items-center gap-2">
              <FallbackComponent className="size-5 text-[#faf9f5]" />
              <span className="font-medium text-[#faf9f5] text-base leading-[22px]">
                Instagram
              </span>
            </div>
            <div className="flex flex-col gap-2">
              <div className="flex justify-between items-center">
                <span className="font-normal text-[#a09d96] text-sm leading-5">
                  Daily Video Limit
                </span>
                <span className="font-medium text-[#faf9f5] text-[13px]">
                  30 videos
                </span>
              </div>
              <div className="relative rounded-full bg-[#1f1e1b] w-full h-1">
                <div className="w-[15%] rounded-full bg-[#cc785c] absolute left-0 top-0 h-1" />
                <div className="top-1/2 left-[15%] -translate-x-1/2 -translate-y-1/2 size-4 rounded-full bg-[#cc785c] absolute" />
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <div className="flex justify-between items-center">
                <span className="font-normal text-[#a09d96] text-sm leading-5">
                  Daily Time Limit
                </span>
                <span className="font-medium text-[#faf9f5] text-[13px]">
                  60 min
                </span>
              </div>
              <div className="relative rounded-full bg-[#1f1e1b] w-full h-1">
                <div className="w-[25%] rounded-full bg-[#cc785c] absolute left-0 top-0 h-1" />
                <div className="top-1/2 left-[25%] -translate-x-1/2 -translate-y-1/2 size-4 rounded-full bg-[#cc785c] absolute" />
              </div>
            </div>
            <div className="border-[#2f2c28] border-t-1 border-r-0 border-b-0 border-l-0 border-solid flex pt-2 justify-between items-center">
              <span className="font-normal text-[#a09d96] text-sm leading-5">
                Enable App Blocking
              </span>
              <div className="shrink-0 rounded-full bg-[#cc785c] flex p-1 justify-end items-center w-12 h-7">
                <div className="size-5 rounded-full bg-white" />
              </div>
            </div>
          </div>
          <div className="rounded-xl bg-[#252320] border-[#2f2c28] border-1 border-solid flex p-6 flex-col gap-4 w-full">
            <div className="flex items-center gap-2">
              <FallbackComponent className="size-5 text-[#faf9f5]" />
              <span className="font-medium text-[#faf9f5] text-base leading-[22px]">
                YouTube
              </span>
            </div>
            <div className="flex flex-col gap-2">
              <div className="flex justify-between items-center">
                <span className="font-normal text-[#a09d96] text-sm leading-5">
                  Daily Video Limit
                </span>
                <span className="font-medium text-[#faf9f5] text-[13px]">
                  15 videos
                </span>
              </div>
              <div className="relative rounded-full bg-[#1f1e1b] w-full h-1">
                <div className="w-[8%] rounded-full bg-[#cc785c] absolute left-0 top-0 h-1" />
                <div className="top-1/2 left-[8%] -translate-x-1/2 -translate-y-1/2 size-4 rounded-full bg-[#cc785c] absolute" />
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <div className="flex justify-between items-center">
                <span className="font-normal text-[#a09d96] text-sm leading-5">
                  Daily Time Limit
                </span>
                <span className="font-medium text-[#faf9f5] text-[13px]">
                  90 min
                </span>
              </div>
              <div className="relative rounded-full bg-[#1f1e1b] w-full h-1">
                <div className="w-[37%] rounded-full bg-[#cc785c] absolute left-0 top-0 h-1" />
                <div className="top-1/2 left-[37%] -translate-x-1/2 -translate-y-1/2 size-4 rounded-full bg-[#cc785c] absolute" />
              </div>
            </div>
            <div className="border-[#2f2c28] border-t-1 border-r-0 border-b-0 border-l-0 border-solid flex pt-2 justify-between items-center">
              <span className="font-normal text-[#a09d96] text-sm leading-5">
                Enable App Blocking
              </span>
              <div className="shrink-0 rounded-full bg-[#2f2c28] flex p-1 justify-start items-center w-12 h-7">
                <div className="size-5 rounded-full bg-white" />
              </div>
            </div>
          </div>
          <div className="rounded-xl bg-[#252320] border-[#2f2c28] border-1 border-solid flex p-6 flex-col gap-4 w-full">
            <div className="flex items-center gap-2">
              <Music2 className="size-5 text-[#faf9f5]" />
              <span className="font-medium text-[#faf9f5] text-base leading-[22px]">
                TikTok
              </span>
            </div>
            <div className="flex flex-col gap-2">
              <div className="flex justify-between items-center">
                <span className="font-normal text-[#a09d96] text-sm leading-5">
                  Daily Video Limit
                </span>
                <span className="font-medium text-[#faf9f5] text-[13px]">
                  50 videos
                </span>
              </div>
              <div className="relative rounded-full bg-[#1f1e1b] w-full h-1">
                <div className="w-[25%] rounded-full bg-[#cc785c] absolute left-0 top-0 h-1" />
                <div className="top-1/2 left-[25%] -translate-x-1/2 -translate-y-1/2 size-4 rounded-full bg-[#cc785c] absolute" />
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <div className="flex justify-between items-center">
                <span className="font-normal text-[#a09d96] text-sm leading-5">
                  Daily Time Limit
                </span>
                <span className="font-medium text-[#faf9f5] text-[13px]">
                  45 min
                </span>
              </div>
              <div className="relative rounded-full bg-[#1f1e1b] w-full h-1">
                <div className="w-[18%] rounded-full bg-[#cc785c] absolute left-0 top-0 h-1" />
                <div className="top-1/2 left-[18%] -translate-x-1/2 -translate-y-1/2 size-4 rounded-full bg-[#cc785c] absolute" />
              </div>
            </div>
            <div className="border-[#2f2c28] border-t-1 border-r-0 border-b-0 border-l-0 border-solid flex pt-2 justify-between items-center">
              <span className="font-normal text-[#a09d96] text-sm leading-5">
                Enable App Blocking
              </span>
              <div className="shrink-0 rounded-full bg-[#cc785c] flex p-1 justify-end items-center w-12 h-7">
                <div className="size-5 rounded-full bg-white" />
              </div>
            </div>
          </div>
          <div className="rounded-xl bg-[#252320] border-[#2f2c28] border-1 border-solid flex p-6 flex-col gap-4 w-full">
            <div className="flex items-center gap-2">
              <Ghost className="size-5 text-[#faf9f5]" />
              <span className="font-medium text-[#faf9f5] text-base leading-[22px]">
                Snapchat
              </span>
            </div>
            <div className="flex flex-col gap-2">
              <div className="flex justify-between items-center">
                <span className="font-normal text-[#a09d96] text-sm leading-5">
                  Daily Video Limit
                </span>
                <span className="font-medium text-[#faf9f5] text-[13px]">
                  20 videos
                </span>
              </div>
              <div className="relative rounded-full bg-[#1f1e1b] w-full h-1">
                <div className="w-[10%] rounded-full bg-[#cc785c] absolute left-0 top-0 h-1" />
                <div className="top-1/2 left-[10%] -translate-x-1/2 -translate-y-1/2 size-4 rounded-full bg-[#cc785c] absolute" />
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <div className="flex justify-between items-center">
                <span className="font-normal text-[#a09d96] text-sm leading-5">
                  Daily Time Limit
                </span>
                <span className="font-medium text-[#faf9f5] text-[13px]">
                  30 min
                </span>
              </div>
              <div className="relative rounded-full bg-[#1f1e1b] w-full h-1">
                <div className="w-[12%] rounded-full bg-[#cc785c] absolute left-0 top-0 h-1" />
                <div className="top-1/2 left-[12%] -translate-x-1/2 -translate-y-1/2 size-4 rounded-full bg-[#cc785c] absolute" />
              </div>
            </div>
            <div className="border-[#2f2c28] border-t-1 border-r-0 border-b-0 border-l-0 border-solid flex pt-2 justify-between items-center">
              <span className="font-normal text-[#a09d96] text-sm leading-5">
                Enable App Blocking
              </span>
              <div className="shrink-0 rounded-full bg-[#2f2c28] flex p-1 justify-start items-center w-12 h-7">
                <div className="size-5 rounded-full bg-white" />
              </div>
            </div>
          </div>
        </div>
        <div className="fixed bg-[#181715] border-[#2f2c28] border-t-1 border-r-0 border-b-0 border-l-0 border-solid inset-x-0 bottom-0 p-6">
          <button className="leading-none font-medium rounded-md bg-[#cc785c] text-white text-sm leading-5 flex justify-center items-center gap-2 w-full h-10">
            <Check className="size-4" />
            Save Changes
          </button>
        </div>
      </div>
    </div>
  );
}
