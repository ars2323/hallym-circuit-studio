// 화면 해상도 바꾸기(CI windows-smoke, X-05). pwsh Add-Type로 읽어 [Display]::Set(1920, 1080)을 부른다.
// 0이면 성공(DISP_CHANGE_SUCCESSFUL). 가상 화면이 그 해상도를 못 내면 다른 값이 온다.
using System;
using System.Runtime.InteropServices;

public class Display {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
    public struct DEVMODE {
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string dmDeviceName;
        public short dmSpecVersion; public short dmDriverVersion; public short dmSize; public short dmDriverExtra;
        public int dmFields; public int dmPositionX; public int dmPositionY; public int dmDisplayOrientation;
        public int dmDisplayFixedOutput; public short dmColor; public short dmDuplex; public short dmYResolution;
        public short dmTTOption; public short dmCollate;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string dmFormName;
        public short dmLogPixels; public int dmBitsPerPel; public int dmPelsWidth; public int dmPelsHeight;
        public int dmDisplayFlags; public int dmDisplayFrequency; public int dmICMMethod; public int dmICMIntent;
        public int dmMediaType; public int dmDitherType; public int dmReserved1; public int dmReserved2;
        public int dmPanningWidth; public int dmPanningHeight;
    }

    [DllImport("user32.dll")] public static extern bool EnumDisplaySettings(string deviceName, int modeNum, ref DEVMODE devMode);
    [DllImport("user32.dll")] public static extern int ChangeDisplaySettings(ref DEVMODE devMode, int flags);

    public static int Set(int width, int height) {
        DEVMODE dm = new DEVMODE();
        dm.dmSize = (short) Marshal.SizeOf(typeof(DEVMODE));
        if (!EnumDisplaySettings(null, -1, ref dm)) {
            return -99;
        }
        dm.dmPelsWidth = width;
        dm.dmPelsHeight = height;
        dm.dmFields = 0x80000 | 0x100000; // DM_PELSWIDTH | DM_PELSHEIGHT
        return ChangeDisplaySettings(ref dm, 0);
    }
}
