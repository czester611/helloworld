#!/usr/bin/env python3
"""Parametric case: Raspberry Pi 3 B+ with a 2.42" OLED (SSD1309 128x64) in the lid.

Usage:  python3 build_case.py [waveshare|solder72] [outdir]
Needs:  pip install trimesh manifold3d numpy

Coordinates: origin = bottom-left corner of the Pi board seen from above,
x right (85 mm), y up (56 mm), z up from the underside of the case floor.
Units: mm. All sizes are the NOMINAL sizes of the parts; the fit
clearances (PI_CLR, FIT) are what you tune after a test print.
"""
import sys
import numpy as np
import trimesh
from trimesh.creation import box as _box, cylinder as _cyl

# ---------------------------------------------------------------- parameters
OLED_PRESETS = {
    # Waveshare 2.42" module (61.5 x 39.5, no known mounting-hole pattern)
    "waveshare": dict(pcb=(61.5, 39.5), pcb_t=1.6, holes=None, hole_d=None),
    # Common 7-pin module sold e.g. by universal-solder: 72 x 43.5, holes 68 x 39, d 2.8
    "solder72": dict(pcb=(72.0, 43.5), pcb_t=1.6, holes=(68.0, 39.0), hole_d=2.8),
}
ACTIVE = (55.01, 27.49)          # display active area (both vendors)
WINDOW_MARGIN = 1.2              # window = active area + margin on each side
WINDOW_OFFSET = (0.0, 0.0)       # shift window vs module centre (x, y) - measure!

PI = (85.0, 56.0)                # board size
PI_T = 1.4                       # PCB thickness
PI_HOLES = ((3.5, 3.5), (61.5, 3.5), (3.5, 52.5), (61.5, 52.5))
PI_CLR = 0.5                     # air gap board -> inner wall
WALL = 2.0
FLOOR = 2.0
STANDOFF_H = 4.0                 # PCB underside above floor top
STANDOFF_D = 6.0
STANDOFF_HOLE = 2.1              # self-tapping M2.5
TOP_CLEAR = 17.5                 # free height above PCB top (USB/ETH ~16)
LID_PLATE = 5.2                  # 2.0 bezel + 3.2 pocket for OLED module
BEZEL = 2.0
POCKET_CLR = 0.25                # per side around OLED PCB
FIT = 0.25                       # lid lip clearance per side
LIP_W, LIP_H = 1.6, 2.0
EAR_R, EAR_OFF = 4.5, 4.5        # corner screw ears (centre offset outside the corner)
EAR_BASE_T, EAR_LID_T = 5.0, 3.0
EAR_HOLE_BASE, EAR_HOLE_LID = 2.1, 2.7   # M2.5 x 8 screw
VENT_SLOTS = 6


def bx(x0, y0, z0, x1, y1, z1):
    b = _box(extents=(x1 - x0, y1 - y0, z1 - z0))
    b.apply_translation(((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2))
    return b


def cy(x, y, z0, z1, r, sections=48):
    c = _cyl(radius=r, height=z1 - z0, sections=sections)
    c.apply_translation((x, y, (z0 + z1) / 2))
    return c


def union(parts):
    return trimesh.boolean.union(parts, engine="manifold")


def diff(a, subs):
    return trimesh.boolean.difference([a] + list(subs), engine="manifold")


def build(preset_name):
    oled = OLED_PRESETS[preset_name]
    cav_x0, cav_y0 = -PI_CLR, -PI_CLR
    cav_x1, cav_y1 = PI[0] + PI_CLR, PI[1] + PI_CLR
    out_x0, out_y0 = cav_x0 - WALL, cav_y0 - WALL
    out_x1, out_y1 = cav_x1 + WALL, cav_y1 + WALL
    z_floor_top = FLOOR
    z_pcb_bot = z_floor_top + STANDOFF_H
    z_pcb_top = z_pcb_bot + PI_T
    z_rim = z_pcb_top + TOP_CLEAR          # top of base walls / underside of lid plate

    # ---------------------------------------------------------------- base
    outer = bx(out_x0, out_y0, 0, out_x1, out_y1, z_rim)
    cavity = bx(cav_x0, cav_y0, z_floor_top, cav_x1, cav_y1, z_rim + 1)
    base = diff(outer, [cavity])

    parts = [base]
    for (hx, hy) in PI_HOLES:                       # board standoffs
        parts.append(cy(hx, hy, z_floor_top - 0.1, z_pcb_bot, STANDOFF_D / 2))
    for sx in (-1, 1):                              # corner ears
        for sy in (-1, 1):
            cx = (out_x0 - (EAR_OFF - 2.5)) if sx < 0 else (out_x1 + (EAR_OFF - 2.5))
            cyy = (out_y0 - (EAR_OFF - 2.5)) if sy < 0 else (out_y1 + (EAR_OFF - 2.5))
            ear = cy(cx, cyy, z_rim - EAR_BASE_T, z_rim, EAR_R)
            # fill towards the case so the ear is not just touching
            fillx = bx(min(cx, (out_x0 if sx < 0 else out_x1)) - 0.0,
                       min(cyy, (out_y0 if sy < 0 else out_y1)),
                       z_rim - EAR_BASE_T,
                       max(cx, (out_x0 if sx < 0 else out_x1)),
                       max(cyy, (out_y0 if sy < 0 else out_y1)),
                       z_rim)
            parts += [ear, fillx]
    base = union(parts)

    # cut-outs (open to the rim - the lid plate closes them from above)
    notch_z0 = z_pcb_top - 0.5
    cuts = []
    cuts.append(bx(cav_x1 - 0.5, 1.5, notch_z0, out_x1 + 1, PI[1] - 1.5, z_rim + 1))    # ETH + USB (+X)
    cuts.append(bx(4.0, out_y0 - 1, notch_z0, 62.0, cav_y0 + 0.5, z_rim + 1))          # power, HDMI, audio (-Y)
    cuts.append(bx(out_x0 - 1, 20.0, z_pcb_bot - 2.0, cav_x0 + 0.5, 36.0, z_pcb_bot + 3.0))  # SD card (-X)
    for (hx, hy) in PI_HOLES:
        cuts.append(cy(hx, hy, z_floor_top, z_pcb_bot + 0.01, STANDOFF_HOLE / 2, 24))
    for sx in (-1, 1):
        for sy in (-1, 1):
            cx = (out_x0 - (EAR_OFF - 2.5)) if sx < 0 else (out_x1 + (EAR_OFF - 2.5))
            cyy = (out_y0 - (EAR_OFF - 2.5)) if sy < 0 else (out_y1 + (EAR_OFF - 2.5))
            cuts.append(cy(cx, cyy, z_rim - EAR_BASE_T + 0.4, z_rim + 1, EAR_HOLE_BASE / 2, 24))
    slot_pitch = 7.0
    for i in range(VENT_SLOTS):                      # floor ventilation under the board
        x = 14.0 + i * slot_pitch
        cuts.append(bx(x, 12.0, -1, x + 3.0, 44.0, z_floor_top + 1))
    base = diff(base, cuts)

    # ---------------------------------------------------------------- lid
    plate = bx(out_x0, out_y0, z_rim, out_x1, out_y1, z_rim + LID_PLATE)
    lparts = [plate]
    for sx in (-1, 1):
        for sy in (-1, 1):
            cx = (out_x0 - (EAR_OFF - 2.5)) if sx < 0 else (out_x1 + (EAR_OFF - 2.5))
            cyy = (out_y0 - (EAR_OFF - 2.5)) if sy < 0 else (out_y1 + (EAR_OFF - 2.5))
            ztop = z_rim + LID_PLATE
            lparts.append(cy(cx, cyy, ztop - EAR_LID_T, ztop, EAR_R))
            lparts.append(bx(min(cx, (out_x0 if sx < 0 else out_x1)),
                             min(cyy, (out_y0 if sy < 0 else out_y1)),
                             ztop - EAR_LID_T,
                             max(cx, (out_x0 if sx < 0 else out_x1)),
                             max(cyy, (out_y0 if sy < 0 else out_y1)), ztop))
    # lip ring that centres the lid inside the base walls
    lip_out = bx(cav_x0 + FIT, cav_y0 + FIT, z_rim - LIP_H, cav_x1 - FIT, cav_y1 - FIT, z_rim + 0.01)
    lip_in = bx(cav_x0 + FIT + LIP_W, cav_y0 + FIT + LIP_W, z_rim - LIP_H - 1,
                cav_x1 - FIT - LIP_W, cav_y1 - FIT - LIP_W, z_rim + 1)
    lparts.append(diff(lip_out, [lip_in]))
    lid = union(lparts)

    ccx, ccy = (cav_x0 + cav_x1) / 2, (cav_y0 + cav_y1) / 2
    pw, ph = oled["pcb"]
    pocket = bx(ccx - pw / 2 - POCKET_CLR, ccy - ph / 2 - POCKET_CLR, z_rim - 0.01,
                ccx + pw / 2 + POCKET_CLR, ccy + ph / 2 + POCKET_CLR, z_rim + LID_PLATE - BEZEL)
    wx = ACTIVE[0] + 2 * WINDOW_MARGIN
    wy = ACTIVE[1] + 2 * WINDOW_MARGIN
    window = bx(ccx + WINDOW_OFFSET[0] - wx / 2, ccy + WINDOW_OFFSET[1] - wy / 2, z_rim,
                ccx + WINDOW_OFFSET[0] + wx / 2, ccy + WINDOW_OFFSET[1] + wy / 2, z_rim + LID_PLATE + 1)
    lcuts = [pocket, window]
    # same notches as the base so the lip does not hit the overhanging connectors
    lcuts.append(bx(cav_x1 - FIT - LIP_W - 0.5, 1.5, z_rim - LIP_H - 1, out_x1 + 1, PI[1] - 1.5, z_rim + 0.01))
    lcuts.append(bx(4.0, out_y0 - 1, z_rim - LIP_H - 1, 62.0, cav_y0 + FIT + LIP_W + 0.5, z_rim + 0.01))
    for sx in (-1, 1):
        for sy in (-1, 1):
            cx = (out_x0 - (EAR_OFF - 2.5)) if sx < 0 else (out_x1 + (EAR_OFF - 2.5))
            cyy = (out_y0 - (EAR_OFF - 2.5)) if sy < 0 else (out_y1 + (EAR_OFF - 2.5))
            lcuts.append(cy(cx, cyy, z_rim, z_rim + LID_PLATE + 1, EAR_HOLE_LID / 2, 24))
    lid = diff(lid, lcuts)
    if oled["holes"]:                                # locating pegs for the module holes
        hx, hy = oled["holes"]
        pegs = []
        for sx in (-1, 1):
            for sy in (-1, 1):
                pegs.append(cy(ccx + sx * hx / 2, ccy + sy * hy / 2, z_rim + LID_PLATE - BEZEL - 1.4,
                               z_rim + LID_PLATE - BEZEL + 0.01, (oled["hole_d"] - 0.3) / 2, 24))
        lid = union([lid] + pegs)

    info = dict(z_pcb_bot=z_pcb_bot, z_pcb_top=z_pcb_top, z_rim=z_rim,
                outer=(out_x1 - out_x0, out_y1 - out_y0), center=(ccx, ccy), oled=oled)
    return base, lid, info


def dummy_pi(info):
    """Rough solid model of the Pi 3 B+ plus the OLED module, for collision tests."""
    zb, zt = info["z_pcb_bot"], info["z_pcb_top"]
    p = [bx(0, 0, zb, PI[0], PI[1], zt)]
    p.append(bx(66.0, 2.25, zt, 87.0, 18.25, zt + 13.5))        # ethernet (overhang 2)
    p.append(bx(69.6, 22.5, zt, 87.0, 35.5, zt + 15.6))         # USB stack
    p.append(bx(69.6, 40.5, zt, 87.0, 53.5, zt + 15.6))         # USB stack
    p.append(bx(7.1, 49.9, zt, 57.9, 55.1, zt + 8.5))           # GPIO header
    p.append(bx(10.6 - 4.0, -1.5, zt, 10.6 + 4.0, 6.0, zt + 2.8))   # micro USB power
    p.append(bx(32.0 - 7.5, -1.5, zt, 32.0 + 7.5, 11.5, zt + 6.5))  # HDMI
    p.append(bx(53.5 - 3.5, -2.0, zt, 53.5 + 3.5, 13.0, zt + 6.0))  # audio jack
    p.append(bx(-2.0, 28.0 - 5.7, zb - 1.5, 12.0, 28.0 + 5.7, zb))  # SD card + socket
    cx, cy_ = info["center"]
    w, h = info["oled"]["pcb"]
    z_top_lid = info["z_rim"] + LID_PLATE
    p_oled = bx(cx - w / 2, cy_ - h / 2, info["z_rim"], cx + w / 2, cy_ + h / 2,
                z_top_lid - BEZEL)
    if info["oled"]["holes"]:                       # the module's own mounting holes
        hx, hy = info["oled"]["holes"]
        hd = info["oled"]["hole_d"]
        holes = [cy(cx + sx * hx / 2, cy_ + sy * hy / 2, info["z_rim"] - 1, z_top_lid, hd / 2, 24)
                 for sx in (-1, 1) for sy in (-1, 1)]
        p_oled = diff(p_oled, holes)
    return union(p), p_oled


def overlap_volume(a, b):
    inter = trimesh.boolean.intersection([a, b], engine="manifold")
    return 0.0 if inter.is_empty else float(inter.volume)


def main():
    preset = sys.argv[1] if len(sys.argv) > 1 else "waveshare"
    outdir = sys.argv[2] if len(sys.argv) > 2 else "."
    base, lid, info = build(preset)
    pi, oled = dummy_pi(info)

    report = {
        "base watertight": base.is_watertight,
        "lid watertight": lid.is_watertight,
        "base volume mm3": round(base.volume, 1),
        "lid volume mm3": round(lid.volume, 1),
        "Pi vs base overlap mm3": round(overlap_volume(pi, base), 3),
        "Pi vs lid overlap mm3": round(overlap_volume(pi, lid), 3),
        "OLED vs lid overlap mm3": round(overlap_volume(oled, lid), 3),
        "base bbox": np.round(base.bounds[1] - base.bounds[0], 2).tolist(),
        "lid bbox": np.round(lid.bounds[1] - lid.bounds[0], 2).tolist(),
    }
    for k, v in report.items():
        print(f"{k}: {v}")

    # print orientation: base as is; lid flipped so the display face lies on the bed
    lid_print = lid.copy()
    lid_print.apply_transform(trimesh.transformations.rotation_matrix(np.pi, (1, 0, 0)))
    lid_print.apply_translation(-lid_print.bounds[0] + [0, 0, 0])
    lid_print.apply_translation((0, 0, 0))
    base.export(f"{outdir}/base_{preset}.stl")
    lid_print.export(f"{outdir}/lid_{preset}.stl")
    return base, lid, report


if __name__ == "__main__":
    main()
