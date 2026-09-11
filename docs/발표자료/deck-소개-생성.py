# -*- coding: utf-8 -*-
"""MLsoft 연차·복리후생 관리 시스템 V2 소개·보고용 PPT 생성. 원본은 이 스크립트이며 슬라이드는 직접 수정하지 않는다.

사용법:
    python -X utf8 docs/발표자료/deck-소개-생성.py
    python -X utf8 docs/발표자료/deck-소개-생성.py --output 다른경로.pptx
    python -X utf8 docs/발표자료/deck-소개-생성.py --export-png 임시검수폴더

필수: python-pptx 1.0.2. PNG 내보내기는 Windows의 PowerPoint COM을 사용한다.
모든 흐름도·아키텍처·표는 편집 가능한 PowerPoint 네이티브 개체다.
2026-09-11 코드 대조: 취소 시점, 총관리자 자기 결재, 파기 전 재입사,
기존 감사 로그 보존 범위, 이메일 총 3회 시도 상한을 브리프보다 우선한다.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE, MSO_CONNECTOR
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR, MSO_AUTO_SIZE
from pptx.oxml.xmlchemy import OxmlElement
from pptx.util import Inches, Pt


# 읽기용 자료에 맞춘 밝은 바탕과 연차ON의 네이비·파랑 계열.
FONT = "맑은 고딕"
BG = "F7F9FC"
INK = "172B49"
MUTED = "53657D"
BLUE = "245BCE"
TEAL = "087E8B"
LINE = "8C9CB2"
PALE = "EAF0FA"
WHITE = "FFFFFF"
GREEN = "176B50"
GREEN_BG = "E5F3ED"
RED = "A13B4A"
RED_BG = "FAE9EC"
GOLD = "8C610D"
GOLD_BG = "FFF2D3"
NAVY = "0D203D"
WIDTH, HEIGHT = 13.333333, 7.5
prs = Presentation()
prs.slide_width, prs.slide_height = Inches(WIDTH), Inches(HEIGHT)
prs.core_properties.title = "MLsoft 사내 연차·복리후생 관리 시스템 V2"
prs.core_properties.subject = "미완성본 · 2026-09-11 기준"
prs.core_properties.author = "MLsoft"
prs.core_properties.language = "ko-KR"


def rgb(value):
    return RGBColor.from_string(value)


def fill(shape, color):
    shape.fill.solid()
    shape.fill.fore_color.rgb = rgb(color)


def format_frame(frame, content, size=18, color=INK, bold=False,
                 align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, margin=0):
    frame.clear()
    frame.word_wrap = True
    frame.auto_size = MSO_AUTO_SIZE.NONE
    frame.vertical_anchor = anchor
    frame.margin_left = frame.margin_right = Inches(margin)
    frame.margin_top = frame.margin_bottom = Inches(margin)
    for index, line in enumerate(content.split("\n")):
        p = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
        p.alignment = align
        p.line_spacing = 1.13
        p.space_before = p.space_after = Pt(0)
        r = p.add_run()
        r.text = line
        r.font.name = FONT
        r.font.size = Pt(size)
        r.font.bold = bold
        r.font.color.rgb = rgb(color)
        # 동아시아 글꼴을 지정해 다른 PC의 테마 글꼴로 바뀌지 않도록 한다.
        prop = r._r.get_or_add_rPr()
        east = OxmlElement("a:ea")
        east.set("typeface", FONT)
        prop.append(east)
        prop.set("lang", "ko-KR")


def text(s, x, y, w, h, content, size=18, color=INK, bold=False,
         align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP):
    shape = s.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    format_frame(shape.text_frame, content, size, color, bold, align, anchor)
    return shape


def rule(s, x, y, w, color=LINE, width=0.7):
    c = s.shapes.add_connector(MSO_CONNECTOR.STRAIGHT,
                               Inches(x), Inches(y), Inches(x + w), Inches(y))
    c.line.color.rgb = rgb(color)
    c.line.width = Pt(width)
    c.shadow.inherit = False
    return c


def node(s, x, y, w, h, title, body="", tone="normal", size=18, body_size=15):
    palette = {
        "normal": (WHITE, INK, LINE), "blue": (PALE, BLUE, BLUE),
        "green": (GREEN_BG, GREEN, GREEN), "red": (RED_BG, RED, RED),
        "gold": (GOLD_BG, GOLD, GOLD), "navy": (NAVY, WHITE, NAVY),
    }
    fc, tc, lc = palette[tone]
    shape = s.shapes.add_shape(MSO_SHAPE.RECTANGLE,
                              Inches(x), Inches(y), Inches(w), Inches(h))
    fill(shape, fc)
    shape.line.color.rgb = rgb(lc)
    shape.line.width = Pt(1.0)
    shape.shadow.inherit = False
    format_frame(shape.text_frame, title + ("\n" + body if body else ""),
                 size, tc, True, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE, 0.10)
    for p in shape.text_frame.paragraphs[1:]:
        for run in p.runs:
            run.font.size = Pt(body_size)
            run.font.bold = False
    return shape


def decision(s, x, y, w, h, title, size=16):
    shape = s.shapes.add_shape(MSO_SHAPE.DIAMOND,
                              Inches(x), Inches(y), Inches(w), Inches(h))
    fill(shape, GOLD_BG)
    shape.line.color.rgb = rgb(GOLD)
    shape.line.width = Pt(1.1)
    shape.shadow.inherit = False
    format_frame(shape.text_frame, title, size, GOLD, True, PP_ALIGN.CENTER,
                 MSO_ANCHOR.MIDDLE, 0)
    shape.text_frame.word_wrap = False
    return shape


def arrow(s, points, color=LINE, dashed=False, both=False):
    """직교 선분을 네이티브 커넥터로 연결하고 마지막 선분에만 화살표를 붙인다."""
    # 화살표의 조건은 별도 텍스트로 배치해 선과 글자가 겹치지 않게 한다.
    for i, (start, end) in enumerate(zip(points, points[1:])):
        assert start[0] == end[0] or start[1] == end[1], "대각선 연결 금지"
        c = s.shapes.add_connector(MSO_CONNECTOR.STRAIGHT,
                                  Inches(start[0]), Inches(start[1]),
                                  Inches(end[0]), Inches(end[1]))
        c.line.color.rgb = rgb(color)
        c.line.width = Pt(1.5)
        c.shadow.inherit = False
        ln = c._element.spPr.get_or_add_ln()
        if dashed:
            dash = OxmlElement("a:prstDash")
            dash.set("val", "dash")
            ln.append(dash)
        if both and i == 0:
            start_arrow = OxmlElement("a:headEnd")
            start_arrow.set("type", "triangle")
            start_arrow.set("w", "sm")
            start_arrow.set("len", "sm")
            ln.append(start_arrow)
        if i == len(points) - 2:
            end_arrow = OxmlElement("a:tailEnd")
            end_arrow.set("type", "triangle")
            end_arrow.set("w", "sm")
            end_arrow.set("len", "sm")
            ln.append(end_arrow)


def label(s, x, y, w, content, color=MUTED, size=13):
    return text(s, x, y, w, 0.45, content, size, color, align=PP_ALIGN.CENTER)


def new_slide(title, subtitle="", source="", dark=False):
    s = prs.slides.add_slide(prs.slide_layouts[6])
    s.background.fill.solid()
    s.background.fill.fore_color.rgb = rgb(NAVY if dark else BG)
    if title:
        text(s, 0.65, 0.48, 12.0, 0.65, title, 32, WHITE if dark else INK, True)
    if subtitle:
        text(s, 0.68, 1.23, 12.0, 0.60, subtitle, 16.5, "B8CBE5" if dark else MUTED)
    text(s, 11.98, 7.03, 0.65, 0.25, f"{len(prs.slides):02}",
         11, "B8CBE5" if dark else MUTED, align=PP_ALIGN.RIGHT)
    if source:
        s.notes_slide.notes_text_frame.text = "내용 확인 근거(2026-09-11):\n" + source
    return s


def footnote(s, content, color=MUTED):
    text(s, 0.68, 6.63, 11.95, 0.40, content, 13, color)


def rows(s, items, y=2.00, step=1.00):
    """소개 장표의 평면 행 구성. 별도 카드나 버튼을 만들지 않는다."""
    for i, (title, body) in enumerate(items):
        top = y + i * step
        text(s, 0.70, top, 0.6, 0.45, f"{i + 1:02}", 20, BLUE, True)
        text(s, 1.5, top, 10.95, 0.42, title, 21, INK, True)
        text(s, 1.5, top + 0.48, 10.95, 0.48, body, 16.5, MUTED)
        if i < len(items) - 1:
            rule(s, 1.5, top + step - 0.13, 10.8, "D5DDE9")


def table(s, headers, data, widths, x=0.68, y=1.95, row_h=0.46, sizes=None):
    total_h = row_h * (len(data) + 1)
    shape = s.shapes.add_table(len(data) + 1, len(headers), Inches(x), Inches(y),
                               Inches(sum(widths)), Inches(total_h))
    t = shape.table
    for col, width in zip(t.columns, widths):
        col.width = Inches(width)
    for i, values in enumerate([headers] + data):
        for j, value in enumerate(values):
            cell = t.cell(i, j)
            cell.fill.solid()
            cell.fill.fore_color.rgb = rgb(NAVY if i == 0 else (WHITE if i % 2 else PALE))
            size = 14.5 if sizes is None else sizes[j]
            format_frame(cell.text_frame, value, size,
                         WHITE if i == 0 else INK, i == 0,
                         PP_ALIGN.LEFT, MSO_ANCHOR.MIDDLE, 0.09)
            # 테이블의 테마 글꼴 및 기본 테두리를 명시적으로 대체한다.
            tcpr = cell._tc.get_or_add_tcPr()
            for side in ("lnL", "lnR", "lnT", "lnB"):
                edge = OxmlElement("a:" + side)
                edge.set("w", "6350")
                solid = OxmlElement("a:solidFill")
                color = OxmlElement("a:srgbClr")
                color.set("val", BG)
                solid.append(color)
                edge.append(solid)
                tcpr.append(edge)
    return shape


FLOW = "docs/11-프로젝트-흐름.md"
STATUS = "docs/12-남은-작업.md, 현재 상태 스냅샷"
LEAVE = "backend/src/main/java/com/mlsoft/backend/domain/leave/service/LeaveService.java"
EMAIL = "backend/src/main/java/com/mlsoft/backend/domain/email/service/EmailNotificationPublisher.java"
USER = "backend/src/main/java/com/mlsoft/backend/domain/user/service/UserService.java"


# 01. 표지 — 발송 목적과 기준일을 한눈에 보이게 한다.
s = new_slide("", source="docs/발표자료/브리프-소개-ppt-2026-09-11.md", dark=True)
text(s, 0.86, 1.95, 11.7, 2.10, "MLsoft 사내 연차·복리후생\n관리 시스템 V2", 46, WHITE, True)
text(s, 0.92, 4.20, 11.5, 0.60, "시스템 구성 · 데이터베이스 · 기능 흐름", 24, "B8CBE5")
rule(s, 0.92, 5.08, 11.4, "365276")
text(s, 0.92, 5.47, 11.5, 0.45, "미완성본 · 2026-09-11 기준", 22, "84DCEA", True)

# 15. 전체 아키텍처.
s = new_slide("시스템 전체 구성", "브라우저 화면과 업무 서버를 하나의 애플리케이션 컨테이너로 배포합니다.", "CLAUDE.md, 아키텍처·배포\nDockerfile\ndocker-compose.prod.yml")
node(s, .72, 3.03, 2.22, 1.38, "브라우저", "React 화면", "normal", 24, 19)
node(s, 4.04, 2.72, 4.10, 1.85, "Spring Boot 서버", "화면 파일 제공 + 업무 API\n단일 앱 컨테이너 · 포트 8080", "navy", 24, 16)
arrow(s, [(2.94, 3.72), (4.04, 3.72)], BLUE, both=True)
label(s, 2.92, 3.15, 1.15, "요청·응답", BLUE)
for y, title, body in [(1.90, "Google OAuth2", "회사 계정 로그인"),
                       (3.10, "MySQL 8", "운영 데이터 저장"),
                       (4.30, "SMTP", "이메일 발송"),
                       (5.50, "공공데이터포털", "공휴일 정보 동기화")]:
    node(s, 9.52, y, 3.07, .88, title, body, size=18, body_size=14)
    center = y + .44
    arrow(s, [(8.14, 3.64), (8.76, 3.64), (8.76, center), (9.52, center)], LINE)
node(s, 4.04, 5.38, 4.10, .89, "내부 스케줄러", "매일 00:10 KST · 5개 작업", "blue", 19, 15)
arrow(s, [(6.09, 4.57), (6.09, 5.38)], BLUE, both=True)
footnote(s, "테스트 데이터베이스는 H2를 사용합니다. SMTP는 Gmail 앱 비밀번호 방식의 계정을 연동합니다.")

# 15b. 외부 연동 API — 셋 다 우리 장애로 번지지 않게 설계한 점을 같이 보여 준다.
s = new_slide("외부 연동 API", "세 가지 외부 서비스에 의존하며, 어느 하나가 멈춰도 업무 처리는 계속됩니다.",
              "backend/.../holiday/client/HolidayApiClient.java\nsecurity/CustomOAuth2UserService.java\nemail/service/MailSenderResolver.java\nLeaveScheduler.syncHolidaysForNewYear (0 5 0 1 1 *)")
table(s, ["외부 서비스", "용도 · 호출 시점", "실패하면", "인증 정보 보관"], [
    ["공공데이터포털\n특일정보 API",
     "공휴일 목록 조회 (연 단위)\n매년 1월 1일 00:05 자동 · 관리자 수동 동기화\n캐시가 비어 있을 때 최초 1회",
     "기존 공휴일 캐시 유지, 빈 결과로 계속\n연차 신청은 막히지 않음\n관리자 수동 동기화만 원인별 오류 표시",
     "관리자 화면에서 암호화 저장\n없으면 서버 환경변수"],
    ["Google OAuth2",
     "로그인 (프로필·이메일만 요청)\n회사 도메인 계정만 허용",
     "로그인 불가 · 안내 메시지\n허용 도메인이 아니면 거부",
     "서버 환경변수\n(클라이언트 ID · 시크릿)"],
    ["Gmail SMTP",
     "이메일 발송 (587 · STARTTLS)\n업무 사건 즉시 + 15분마다 재시도",
     "실패 이력 기록 · 총 3회 재시도\n이후 관리자 수동 재발송\n업무 처리에는 영향 없음",
     "관리자 화면에서 암호화 저장\n없으면 서버 환경변수"],
], [2.3, 3.75, 3.6, 2.3], row_h=1.0, sizes=[15, 13.5, 13.5, 13.5])
footnote(s, "자격 증명은 저장만 되고 화면에는 마스킹된 값만 보입니다. 공휴일은 요청마다 외부를 부르지 않고 DB 캐시를 씁니다.")

# 16. 백엔드 내부.
s = new_slide("서버 내부의 업무 구조", "12개 업무 영역을 나누고, 요청 처리와 업무 규칙·데이터 저장의 역할을 분리했습니다.", "CLAUDE.md, 계층 구조\nbackend/src/main/java/com/mlsoft/backend/domain/")
for x, title, body in [(0.73, "요청 접수", "입력과 역할 확인"), (3.87, "업무 규칙 처리", "잔액·승인자·상태 변경"),
                       (7.01, "저장소 접근", "조회와 저장"), (10.15, "업무 데이터", "상태와 이력 보관")]:
    node(s, x, 2.08, 2.47, 1.13, title, body, "blue" if x == 3.87 else "normal", 20, 14.5)
for a, b in [(3.20, 3.87), (6.34, 7.01), (9.48, 10.15)]:
    arrow(s, [(a, 2.64), (b, 2.64)])
text(s, .77, 3.73, 11.8, .45, "12개 업무 영역", 23, BLUE, True)
table(s, ["사람과 조직", "연차와 일정", "운영 지원"], [
    ["인증", "연차", "이메일"], ["구성원", "복리후생", "공휴일"],
    ["부서", "개인 일정", "외부 연동 자격 증명"], ["감사 기록", "정책", "공통 상태"],
], [3.94, 3.94, 3.94], y=4.29, row_h=.40, sizes=[16,16,16])
footnote(s, "인증 정보는 JWT가 담긴 HttpOnly 쿠키로 전달하고, 서버는 최신 계정 상태를 다시 확인합니다.")

# 17. 프론트 구조.
s = new_slide("화면과 데이터의 연결", "17개 화면이 서버 데이터를 공통 방식으로 요청하고 갱신합니다.", "CLAUDE.md, 프론트엔드 데이터 흐름\nfrontend/src/pages/\nfrontend/src/api/index.js")
node(s, .75, 2.13, 3.28, 1.16, "서버 API 연결", "Axios로 요청·응답 처리", "normal", 22, 16)
node(s, 5.03, 2.13, 3.28, 1.16, "데이터 조회·캐시", "React Query로 공통 관리", "blue", 22, 16)
node(s, 9.31, 2.13, 3.28, 1.16, "업무 화면", "페이지가 데이터를 표시", "normal", 22, 16)
arrow(s, [(4.03, 2.71), (5.03, 2.71)], BLUE)
arrow(s, [(8.31, 2.71), (9.31, 2.71)], BLUE)
text(s, .77, 3.95, 5.75, .55, "공통 화면", 23, INK, True)
text(s, .77, 4.71, 5.53, 1.22,
     "대시보드 · 캘린더 · 내역 · 복리후생\n내 정보 · 결재 관리 · 팀 현황\n로그인 · 로그인 연결 · 온보딩", 17, MUTED)
text(s, 7.01, 3.95, 5.53, .55, "관리자 화면", 23, INK, True)
text(s, 7.01, 4.71, 5.53, 1.22,
     "구성원 · 부서 · 연차 정책 · 복리후생 정책\n처리 이력 · 이메일 · 외부 연동", 17, MUTED)
footnote(s, "권한에 맞춰 화면을 열고, 오류 알림을 공통 처리합니다. 디자인 값은 Tailwind v4의 CSS에서 관리합니다.")

# 15c. 데이터베이스 구조 — 사원(users)을 중심으로 21개 테이블을 영역별로 묶어 보여 준다.
s = new_slide("데이터베이스 구조", "사원(users)을 중심으로 21개 테이블이 7개 영역으로 나뉩니다.", "db/schema.sql — CREATE TABLE 21개, FOREIGN KEY 27개")
# 1행
node(s, .68, 1.85, 3.60, 1.35, "조직", "department 부서 (parent_id 계층 · leader_id 팀장)\nemployment_periods 과거 근속 구간", "normal", 17, 12.5)
node(s, 4.75, 1.85, 3.80, 1.35, "연차", "leave_requests 신청 · leave_dates 날짜별 행\nleave_action_history 처리 이력\nleave_reset_history 기산일 스냅샷", "blue", 17, 12.5)
node(s, 8.95, 1.85, 3.68, 1.35, "복리후생", "welfare_policies 정책\nwelfare_requests 신청 (policy_id)\nwelfare_action_history 처리 이력", "normal", 17, 12.5)
# 2행 — 가운데가 users
node(s, .68, 3.42, 3.60, 1.20, "일정 · 공휴일", "schedule_entries 개인 일정 · schedule_dates 날짜\nholidays 공휴일 캐시 (독립)", "normal", 17, 12.5)
node(s, 5.45, 3.50, 2.45, 1.05, "users 사원", "잔액 3필드 · 기산일 · 역할\n온보딩 · 퇴직/파기 상태", "navy", 18, 12.5)
node(s, 8.95, 3.42, 3.68, 1.20, "이메일", "email_history 발송 큐·이력\nleave_reminder_dispatch 소진 안내 기록\nemail_templates 양식", "normal", 17, 12.5)
# 3행
node(s, .68, 4.85, 3.60, 1.35, "정책 · 자격 증명", "leave_policy 근속별 부여 · leave_policy_config 설정 12키\nmail_credentials · holiday_api_credentials (암호화 저장)", "gold", 17, 12.5)
node(s, 4.75, 4.85, 3.80, 1.35, "감사", "admin_audit_log 관리자 조작 기록\nactor_id 누가 · target_user_id 누구에게 · action 13종", "normal", 17, 12.5)
text(s, 9.05, 4.95, 3.5, 1.2, "화살표는 users를 참조하는 외래 키입니다.\n정책·자격 증명은 사원과 무관한 설정입니다.\n파기 시 행을 지우지 않고 값만 익명화합니다.", 13, MUTED)
# 외래 키 화살표 — users에서 각 영역으로
arrow(s, [(6.675, 3.50), (6.675, 3.20)], BLUE)
arrow(s, [(6.675, 4.55), (6.675, 4.85)], LINE)
arrow(s, [(5.45, 4.02), (4.28, 4.02)], LINE)
arrow(s, [(7.90, 4.02), (8.95, 4.02)], LINE)
arrow(s, [(5.45, 3.72), (4.52, 3.72), (4.52, 3.00), (4.28, 3.00)], LINE)
arrow(s, [(7.90, 3.72), (8.72, 3.72), (8.72, 3.00), (8.95, 3.00)], LINE)
text(s, 6.80, 3.21, 1.9, 0.26, "user_id · 승인자 2명", 11, BLUE)
text(s, 6.80, 4.58, 2.1, 0.26, "actor_id · target_user_id", 11, MUTED)
text(s, 4.40, 3.74, 0.95, 0.26, "user_id", 11, MUTED)
text(s, 8.00, 3.74, 0.90, 0.26, "user_id", 11, MUTED)
footnote(s, "연차 일수는 소수 첫째 자리까지 저장합니다. 상태 값은 DB ENUM으로 제한해 잘못된 값이 들어가지 않습니다.")

# 15d. 핵심 테이블 — 자주 보게 될 여섯 테이블의 주요 컬럼.
s = new_slide("핵심 테이블의 주요 항목", "자주 보게 될 여섯 테이블입니다. 컬럼 이름은 실제 스키마 그대로입니다.", "db/schema.sql")
table(s, ["테이블", "무엇을 담나", "주요 컬럼"], [
    ["users\n사원", "계정·역할·소속과 연차 잔액,\n퇴직·파기 상태까지 한 행", "base_days · bonus_days · use_days 잔액 3필드 (advance_days는 파생) · hire_date · last_reset_date 기산일\nrole · onboarding_status · is_active · retired_at · purged_at · purge_hold_reason · version 낙관적 락"],
    ["leave_requests\n+ leave_dates", "연차 신청 1건과\n날짜별 행", "status (PENDING · APPROVED · REJECTED · CANCELLED · CANCEL_PENDING) · leave_type (종일 · 오전 · 오후) · days\nprimary_approver_id · sub_approver_id · request_reason · cancel_reason · advance_used_days · leave_dates.day"],
    ["welfare_requests", "복리후생 신청", "policy_id · category · target · add_days (승인 시 bonus_days에 가산) · status · 승인자 2명 · reason"],
    ["department", "부서 계층과 팀장", "name · parent_id (상위 부서) · leader_id (팀장) · system_default (미배정 부서) · active"],
    ["email_history", "발송 큐이자 이력", "email_type · status (PENDING · SENDING · SENT · FAILED) · retry_count · sending_at · sent_at · error_message\ntitle · content (보낸 HTML 원문 보관)"],
    ["admin_audit_log", "관리자 조작 기록", "actor_id 누가 · action 13종 · target_user_id · target_label 누구에게 · before_value · after_value"],
], [2.05, 2.75, 7.15], row_h=.62, sizes=[13.5, 12.5, 12])
footnote(s, "승인자 2명(기본·서브)은 연차와 복리후생이 같은 방식으로 갖습니다. 처리 이력은 별도 *_action_history 테이블에 쌓입니다.")

# 06. 신청과 결재.
s = new_slide("연차 신청과 결재", "현재 회차의 연차는 신청 즉시 차감됩니다. 승인 시 추가 차감은 없습니다.", LEAVE + "\n" + EMAIL)
node(s, .70, 2.02, 2.30, .85, "사원: 날짜 선택", "종일·오전·오후반차", body_size=14.5)
node(s, 3.45, 2.02, 2.25, .85, "승인자 확인·신청", "기본·서브 승인자", body_size=14.5)
decision(s, 6.10, 1.84, 1.80, 1.22, "신청 가능?", 15)
node(s, 8.35, 1.98, 4.20, .95, "대기 상태 · 현재 회차 선차감", "메일: 신청자 + 기본·서브 승인자", "blue", 18, 14.5)
arrow(s, [(3.00, 2.45), (3.45, 2.45)])
arrow(s, [(5.70, 2.45), (6.10, 2.45)])
arrow(s, [(7.90, 2.45), (8.35, 2.45)], BLUE)
label(s, 7.88, 2.03, .47, "예", BLUE)
arrow(s, [(7.00, 3.06), (7.00, 3.63)], RED)
node(s, 5.77, 3.63, 2.45, .72, "사유 안내 후 수정", tone="red", size=16)
label(s, 7.04, 3.16, .72, "아니오", RED)
text(s, .74, 3.22, 4.62, 1.10, "확인 항목\n주말·공휴일·지난 날짜·중복·잔여일수", 16, MUTED)
arrow(s, [(10.45, 2.93), (10.45, 4.72)], BLUE)
label(s, 10.50, 3.56, 2.12, "승인자 중 한 명이 결재", BLUE, 13)
decision(s, 9.50, 4.72, 1.90, 1.22, "결재 결과", 15)
node(s, 5.05, 4.87, 3.34, .91, "승인 · 잔액 유지", tone="green")
node(s, .75, 4.87, 3.30, .91, "반려 · 잔액 복구", "반려 사유 입력", "red", 18, 14.5)
arrow(s, [(9.50, 5.33), (8.39, 5.33)], GREEN)
label(s, 8.47, 4.94, .88, "승인", GREEN)
arrow(s, [(10.45, 5.94), (10.45, 6.15), (2.40, 6.15), (2.40, 5.78)], RED)
label(s, 6.20, 6.16, 1.0, "반려", RED)
footnote(s, "결재 결과 메일: 신청자 + 기본·서브 승인자 + 재직 총관리자 전원")

# 07. 취소 — 브리프와 다른 현재 동작을 반영한다.
s = new_slide("연차 취소", "승인 여부와 사용 날짜에 따라 즉시 취소 또는 취소 결재로 나뉩니다.", LEAVE + ":413~479\n" + EMAIL)
node(s, .72, 2.25, 2.05, .85, "사원: 취소 요청")
decision(s, 3.28, 1.95, 2.12, 1.46, "승인된 건에\n지난 날짜 포함?", 14.5)
node(s, 6.10, 2.16, 3.00, 1.02, "즉시 취소", "현재 회차 차감분 복구", "green", 20, 15)
text(s, 9.53, 2.13, 3.07, 1.10, "메일\n신청자 + 승인자", 16, MUTED)
arrow(s, [(2.77, 2.68), (3.28, 2.68)])
arrow(s, [(5.40, 2.68), (6.10, 2.68)], GREEN)
label(s, 5.40, 2.23, .7, "아니오", GREEN)
node(s, 3.21, 4.10, 2.30, .88, "취소 결재 대기", "이때는 잔액 유지", "gold", 18, 14.5)
arrow(s, [(4.34, 3.41), (4.34, 4.10)], GOLD)
label(s, 4.48, 3.55, .55, "예", GOLD)
decision(s, 6.13, 3.80, 1.90, 1.45, "취소 승인?", 16)
arrow(s, [(5.51, 4.54), (6.13, 4.54)])
node(s, 9.03, 3.78, 3.54, .86, "취소 완료 · 잔액 복구", tone="green", size=18)
node(s, 9.03, 5.17, 3.54, .86, "승인 상태 유지 · 복구 없음", tone="red", size=17)
arrow(s, [(8.03, 4.53), (8.55, 4.53), (8.55, 4.21), (9.03, 4.21)], GREEN)
label(s, 8.10, 3.81, .80, "승인", GREEN)
arrow(s, [(7.08, 5.25), (7.08, 5.60), (9.03, 5.60)], RED)
label(s, 7.79, 5.18, 1.05, "거부", RED)
text(s, .76, 5.35, 5.03, .97, "취소 요청 메일: 신청자 + 승인자\n취소 결재 결과: 위 수신자 + 총관리자 전원", 14.5, MUTED)
footnote(s, "승인 전 신청, 승인 후 오늘·미래 날짜만 있는 신청은 즉시 취소할 수 있습니다.")

# 08. 회차별 잔액.
s = new_slide("연차 잔액이 계산되는 방식", "기산일은 연차가 새로 부여되는 기준일입니다. 신청 날짜별로 어느 회차인지 확인합니다.", "CLAUDE.md, 연차 잔액 모델\n" + LEAVE)
text(s, .75, 1.97, 11.90, .70, "잔여 연차 = 부여된 연차 + 보너스 연차 − 사용·대기 연차", 27, BLUE, True)
decision(s, .75, 3.31, 2.12, 1.53, "신청 날짜가\n현재 회차인가?", 14)
node(s, 3.42, 2.96, 2.48, .95, "현재 회차 잔액 확인", "기산일 ~ 다음 기산일 전날", "blue", 16, 12.5)
node(s, 3.42, 5.04, 2.48, 1.12, "다음 회차 예약", "허용·한도 내일 때 접수\n현재 잔액은 유지", "normal", 17, 13.5)
arrow(s, [(2.87, 4.075), (3.14, 4.075), (3.14, 3.435), (3.42, 3.435)], BLUE)
label(s, 2.78, 3.04, .6, "예", BLUE)
arrow(s, [(1.81, 4.84), (1.81, 5.60), (3.42, 5.60)])
label(s, 1.90, 5.09, 1.48, "다음 회차", MUTED)
decision(s, 6.40, 2.86, 1.85, 1.15, "잔여 충분?", 14)
arrow(s, [(5.90, 3.435), (6.40, 3.435)])
node(s, 8.85, 2.98, 3.70, .90, "접수 · 현재 회차 선차감", tone="green", size=19)
arrow(s, [(8.25, 3.435), (8.85, 3.435)], GREEN)
label(s, 8.26, 3.01, .58, "예", GREEN)
decision(s, 6.33, 4.45, 2.00, 1.28, "당겨쓰기\n허용·상한 내?", 14)
arrow(s, [(7.325, 4.01), (7.325, 4.45)], GOLD)
label(s, 7.42, 4.06, .95, "아니오", GOLD)
node(s, 8.85, 4.57, 3.70, 1.03, "부족분 기록 후 접수", "다음 기산일의 새 연차에서 상환", "gold", 19, 14)
arrow(s, [(8.33, 5.09), (8.85, 5.09)], GOLD)
label(s, 8.30, 4.68, .54, "예", GOLD)
node(s, 6.32, 6.05, 2.02, .53, "신청 제한", tone="red", size=16)
arrow(s, [(7.33, 5.73), (7.33, 6.05)], RED)
label(s, 7.40, 5.76, 1.0, "아니오", RED)
text(s, .76, 6.68, 11.85, .38, "다음 1회차까지만 예약하며, 예약 한도에는 당겨쓰기를 적용하지 않습니다.", 13, MUTED)

# 09. 온보딩.
s = new_slide("첫 로그인과 입사 정보 확인", "온보딩이 완료되어야 다른 업무 화면을 사용할 수 있습니다.", "backend/src/main/java/com/mlsoft/backend/domain/auth/service/AuthService.java\n" + EMAIL)
node(s, .70, 2.03, 2.10, .86, "Google 로그인", "회사 도메인 확인", body_size=14.5)
node(s, 3.25, 2.03, 2.10, .86, "자동 가입·입력", "생일·입사일·직급", size=16.5, body_size=14)
decision(s, 5.80, 1.86, 2.04, 1.22, "최근 90일 내\n입사일인가?", 14.5)
node(s, 8.40, 2.02, 4.13, .89, "즉시 완료 · 연차 산정", tone="green", size=21)
arrow(s, [(2.80, 2.46), (3.25, 2.46)])
arrow(s, [(5.35, 2.46), (5.80, 2.46)])
arrow(s, [(7.84, 2.47), (8.40, 2.47)], GREEN)
label(s, 7.86, 2.04, .53, "예", GREEN)
node(s, 5.33, 3.70, 2.96, .92, "관리자 승인 대기", "연차 0일 · 총관리자에게 메일", "gold", 18, 13.5)
arrow(s, [(6.82, 3.08), (6.82, 3.70)], GOLD)
label(s, 6.95, 3.20, 1.04, "아니오", GOLD)
decision(s, 9.11, 3.47, 1.90, 1.38, "관리자 결재", 14)
arrow(s, [(8.29, 4.16), (9.11, 4.16)])
node(s, 7.36, 5.44, 2.88, .91, "승인 · 연차 산정", "본인에게 결과 메일", "green", 17.5, 14)
node(s, 10.64, 5.44, 2.00, .91, "반려 · 재입력", "본인에게 메일", "red", 16, 13.5)
arrow(s, [(9.60, 4.51), (9.60, 5.03), (8.80, 5.03), (8.80, 5.44)], GREEN)
label(s, 8.10, 4.70, 1.2, "승인", GREEN)
arrow(s, [(11.01, 4.16), (11.64, 4.16), (11.64, 5.44)], RED)
label(s, 11.67, 4.68, .70, "반려", RED)
text(s, .76, 3.65, 4.07, 2.55,
     "자동 승인 기간은 관리자 설정으로 조정합니다.\n\n수정 허용 시, 대기 중 입사일·생일을 1회 수정할 수 있습니다. 수정 후 다시 판정하며 총관리자에게 알립니다.", 16, MUTED)
footnote(s, "미래 입사일은 입력할 수 없습니다. 반려 메일에는 반려된 입사일을 함께 안내합니다.")

# 10. 복리후생.
s = new_slide("복리후생 신청과 보너스 연차", "정책에 따른 신청이 승인되면 보너스 연차가 쌓입니다.", "backend/src/main/java/com/mlsoft/backend/domain/welfare/service/WelfareService.java\n" + EMAIL)
node(s, .76, 2.48, 2.28, 1.05, "정책 선택", "경조·포상 등", size=21, body_size=17)
node(s, 3.64, 2.48, 2.53, 1.05, "사유 입력·신청", "승인자 확인", size=20, body_size=17)
decision(s, 6.84, 2.23, 2.07, 1.55, "결재 결과", 18)
node(s, 9.70, 2.01, 2.88, 1.10, "승인 · 보너스 가산", tone="green", size=18)
node(s, 9.70, 4.12, 2.88, 1.10, "반려 · 가산 없음", tone="red", size=19)
arrow(s, [(3.04, 3.00), (3.64, 3.00)])
arrow(s, [(6.17, 3.00), (6.84, 3.00)])
arrow(s, [(8.91, 3.00), (9.30, 3.00), (9.30, 2.56), (9.70, 2.56)], GREEN)
label(s, 8.93, 2.13, .70, "승인", GREEN)
arrow(s, [(7.875, 3.78), (7.875, 4.67), (9.70, 4.67)], RED)
label(s, 8.65, 4.23, .85, "반려", RED)
text(s, .78, 4.40, 6.10, 1.5,
     "신청 메일\n신청자 + 기본·서브 승인자\n\n결재 결과 메일\n위 수신자 + 재직 총관리자 전원", 17, MUTED)
footnote(s, "관리자는 복리후생 정책과 정책별 가산 일수를 화면에서 관리합니다.")

# 11. 일일 자동 처리. 분기 대신 고정 순서를 크게 보여 준다.
s = new_slide("매일 00:10 자동 처리", "한국 시간 기준으로 다섯 작업을 정해진 순서대로 실행합니다.", "docs/09-스케줄러-설계.md\nbackend/src/main/java/com/mlsoft/backend/domain/leave/scheduler/LeaveScheduler.java")
steps = [
    ("① 기산일 리셋", "근속별 새 연차 부여\n당겨쓴 일수 상환"),
    ("② 월차 적립", "1년 미만 매월 1일\n입사일 + N개월"),
    ("③ 생일 반차", "보너스 0.5일 가산\n본인·총관리자 메일"),
    ("④ 소진 안내", "잔여 있는 사원에게\n설정 주기별 메일"),
    ("⑤ 퇴직자 파기", "자동 모드만 실행\n보존·예고 확인"),
]
xs = [.70, 3.17, 5.64, 8.11, 10.58]
for i, (title, body) in enumerate(steps):
    node(s, xs[i], 2.48, 2.05, 1.90, title, body, "blue" if i == 0 else "normal", 17, 14)
    if i < 4:
        arrow(s, [(xs[i] + 2.05, 3.43), (xs[i + 1], 3.43)], BLUE)
rule(s, .76, 5.04, 11.8, "CCD6E5")
text(s, .76, 5.36, 5.52, 1.05, "순서를 고정하는 이유\n잔액을 갱신한 뒤 생일 반차와 안내를 처리합니다.", 18, INK)
text(s, 6.84, 5.36, 5.67, 1.05, "사원 1명씩 독립 처리\n한 사원의 오류가 다른 사원에게 번지지 않습니다.", 18, INK)
footnote(s, "소진 안내는 같은 주기 중복 발송을 막습니다. 자동 파기 30일 예고·결과 메일은 총관리자에게 보냅니다.")

# 12. 이메일 발송.
s = new_slide("이메일이 나가는 경로", "업무 결과를 먼저 기록한 뒤 발송하며, 실패 이력은 자동 재시도와 수동 재발송에 사용합니다.", EMAIL + "\nbackend/src/main/java/com/mlsoft/backend/domain/email/service/EmailDeliveryService.java\nbackend/src/main/java/com/mlsoft/backend/domain/email/service/MailSenderResolver.java")
node(s, .75, 2.14, 2.19, 1.00, "업무 사건 발생", "신청·결재·입사·생일 등", size=18, body_size=13.5)
node(s, 3.45, 2.14, 2.30, 1.00, "발송 대기 기록", "업무 저장과 함께 적재", "blue", 18, 14)
node(s, 6.31, 2.14, 2.37, 1.00, "발송 중 선점", "중복 발송 방지", size=18, body_size=14)
node(s, 9.20, 2.14, 3.38, 1.00, "발송 계정 선택 → SMTP", "화면 저장 계정 우선", size=17, body_size=14)
for a, b in [(2.94, 3.45), (5.75, 6.31), (8.68, 9.20)]:
    arrow(s, [(a, 2.64), (b, 2.64)])
text(s, 6.32, 3.44, 2.35, .82, "저장 계정이 없으면\n서버 환경 설정 사용", 14, MUTED)
decision(s, 9.92, 3.76, 1.96, 1.30, "발송 성공?", 16)
arrow(s, [(10.90, 3.14), (10.90, 3.76)])
node(s, 8.88, 5.62, 3.70, .64, "발송 완료", tone="green", size=20)
arrow(s, [(10.90, 5.06), (10.90, 5.62)], GREEN)
label(s, 11.02, 5.16, .7, "예", GREEN)
node(s, 5.70, 4.22, 3.28, .84, "실패 이력 · 자동 재시도", tone="red", size=17)
arrow(s, [(9.92, 4.41), (9.45, 4.41), (9.45, 4.64), (8.98, 4.64)], RED)
label(s, 9.02, 3.81, .9, "아니오", RED)
text(s, .78, 4.15, 4.30, 2.05,
     "관리자가 할 수 있는 일\n제목·본문·변수 편집\n대상 선택·일괄 발송\n이력 조회·실패 건 수동 재발송", 17, INK)
text(s, 5.74, 5.38, 2.88, .94, "15분마다 재시도\n최초 포함 총 3회 실패 시 중단", 14, MUTED)
footnote(s, "메일 발송 경로는 구현되어 있으며, 실제 환경 설정을 마친 뒤 최종 수신 확인이 필요합니다.")

# 13. 퇴직 후 세 경로. 재입사를 파기 뒤에 잇지 않는다.
s = new_slide("퇴직 이후의 개인정보와 재입사", "퇴직 처리 후 복구·재입사·개인정보 파기를 구분해 관리합니다.", USER + ":434~648\nbackend/src/main/java/com/mlsoft/backend/domain/user/service/RetireePurgeService.java\nbackend/src/main/java/com/mlsoft/backend/domain/audit/service/AdminAuditService.java")
node(s, .72, 2.29, 2.23, 1.02, "퇴직 처리", "즉시 로그인 차단", "blue", 21, 16)
node(s, 3.77, 1.95, 3.39, .90, "퇴직 처리 복구", "잘못된 퇴직 처리 되돌리기", size=19, body_size=14)
node(s, 3.77, 3.45, 3.39, 1.05, "파기 전 재입사", "이전 근속 보존 · 새 입사일 적용", "green", 19, 13.5)
node(s, 3.77, 5.23, 3.39, 1.00, "보존 기간 후 파기", "수동 또는 자동 · 보류 가능", "gold", 19, 14)
arrow(s, [(2.95, 2.80), (3.38, 2.80), (3.38, 2.40), (3.77, 2.40)])
arrow(s, [(3.38, 2.80), (3.38, 3.98), (3.77, 3.98)], GREEN)
arrow(s, [(3.38, 3.98), (3.38, 5.73), (3.77, 5.73)], GOLD)
text(s, 7.83, 1.98, 4.74, .91, "로그인 가능 상태로 복구합니다.\n팀장직·이관된 결재는 자동 복구하지 않습니다.", 15.5, MUTED)
text(s, 7.83, 3.42, 4.74, 1.12, "연차는 0일에서 새로 시작합니다.\n과거 근속 구간은 별도로 남깁니다.\n파기된 계정은 신규 가입이 필요합니다.", 15.5, MUTED)
text(s, 7.83, 5.00, 4.79, 1.45, "기본 보존 3년 · 자동 파기는 30일 예고\n계정을 익명화하고 신청·메일 본문을 지웁니다.\n파기 처리 기록은 보존합니다.", 15.5, MUTED)
footnote(s, "본인 계정의 퇴직 처리는 막습니다. 파기된 개인정보는 퇴직 복구나 재입사로 되살릴 수 없습니다.")

# 14. 권한 확인.
s = new_slide("권한과 접근 제어", "사원·팀장·총관리자의 권한을 서버가 요청마다 다시 확인합니다.", "backend/src/main/java/com/mlsoft/backend/security/OnboardingCheckInterceptor.java\n" + LEAVE + ":638~655\n" + USER)
node(s, .73, 2.19, 2.10, 1.00, "화면에서 요청", "로그인 쿠키 전달", size=19, body_size=15)
node(s, 3.43, 2.19, 3.15, 1.00, "서버가 최신 정보 확인", "재직 · 역할 · 온보딩 상태", "blue", 18, 14)
decision(s, 7.21, 2.00, 2.08, 1.40, "접근 가능?", 17)
node(s, 10.06, 1.94, 2.51, .89, "업무 처리", tone="green", size=21)
node(s, 10.06, 3.93, 2.51, .89, "접근 차단·안내", tone="red", size=19)
arrow(s, [(2.83, 2.70), (3.43, 2.70)])
arrow(s, [(6.58, 2.70), (7.21, 2.70)])
arrow(s, [(9.29, 2.70), (9.65, 2.70), (9.65, 2.39), (10.06, 2.39)], GREEN)
label(s, 9.36, 1.97, .65, "예", GREEN)
arrow(s, [(8.25, 3.40), (8.25, 4.37), (10.06, 4.37)], RED)
label(s, 8.48, 3.92, 1.08, "아니오", RED)
rule(s, .78, 5.29, 11.76, "CCD6E5")
text(s, .78, 5.57, 5.44, .99, "결재 권한\n본인 결재는 제한하되 총관리자는 예외입니다.", 17)
text(s, 6.96, 5.57, 5.58, .99, "관리자 계정 보호\n본인 퇴직 처리 금지\n마지막 총관리자의 강등·퇴직 금지", 17)
footnote(s, "역할 변경은 다음 요청부터 반영하며, 결재는 배정된 기본·서브 승인자만 처리합니다.")

# 18. 라이브러리 — 사용 목적까지 포함한 편집 가능한 표.
s = new_slide("백엔드 기술과 라이브러리", "Java 21 · Spring Boot 4.1.0 · Gradle", "backend/build.gradle\n" + STATUS)
table(s, ["기술·라이브러리", "쓰는 이유"], [
    ["spring-boot-starter-webmvc", "브라우저가 호출하는 업무 REST API를 제공한다"],
    ["spring-boot-starter-data-jpa + Hibernate", "업무 객체를 MySQL 데이터와 연결한다"],
    ["spring-boot-starter-security + oauth2-client", "Google 로그인과 역할별 접근을 제어한다"],
    ["jjwt 0.12.6", "JWT 로그인 토큰을 발급하고 검증한다"],
    ["spring-boot-starter-mail", "SMTP 계정으로 이메일을 발송한다"],
    ["spring-boot-starter-validation", "요청 값과 입력 형식을 검증한다"],
    ["Lombok", "반복되는 자바 기본 코드를 줄인다"],
    ["mysql-connector-j / H2", "운영 MySQL 연결 / 테스트용 데이터베이스를 제공한다"],
    ["JUnit 5 + Mockito", "업무 계산·예외·권한 동작을 자동 검증한다"],
], [5.37, 6.59], row_h=.445, sizes=[14,15])
footnote(s, "자동 테스트 기록: 백엔드 500건 · 2026-09-08 실행 기준")

# 19. 프론트 라이브러리.
s = new_slide("프론트엔드 기술과 라이브러리", "React 19.2 · Vite 8 · Node.js", "frontend/package.json\n" + STATUS)
table(s, ["기술·라이브러리", "쓰는 이유"], [
    ["React 19.2 / react-dom", "화면과 입력 상태를 구성한다"],
    ["react-router-dom 7", "화면을 이동하고 권한에 맞춰 접근을 제한한다"],
    ["@tanstack/react-query 5", "서버 데이터 조회·캐시·갱신을 공통 관리한다"],
    ["Axios", "HTTP 요청과 로그인 만료·오류 응답을 처리한다"],
    ["Tailwind CSS 4 / @tailwindcss/vite", "CSS의 공통 디자인 값으로 스타일을 적용한다"],
    ["Day.js", "날짜와 회차를 계산하고 표시한다"],
    ["lucide-react / react-hot-toast", "아이콘과 처리 결과 알림을 표시한다"],
    ["Vitest / Testing Library / jsdom", "화면과 사용자 조작을 자동 검증한다"],
    ["Oxlint", "코드의 기본 오류와 규칙 위반을 검사한다"],
], [5.37,6.59], row_h=.445, sizes=[14.5,15])
footnote(s, "자동 테스트 기록: 프론트엔드 332건 · 2026-09-08 실행 기준")

# 20. 규모와 인프라.
s = new_slide("현재 구현 규모", "코드와 설계 파일을 다시 확인한 2026-09-11 기준입니다.", "코드 실측: HTTP 메서드 매핑 88개, 테스트 제외 페이지 17개, schema.sql CREATE TABLE 21개, 정책 설정 ACTIVE 12개\n" + STATUS + "\nDockerfile\ndocker-compose.prod.yml")
for x, value, unit in [(.77,"88","업무 API"), (3.91,"17","화면"), (7.05,"21","DB 테이블"), (10.19,"12","관리자 설정")]:
    text(s, x, 2.06, 2.35, .97, value, 52, BLUE, True)
    text(s, x, 3.23, 2.5, .45, unit, 21)
rule(s, .77, 4.02, 11.80, "CCD6E5")
text(s, .77, 4.50, 3.15, .83, "832건", 42, INK, True)
text(s, 4.31, 4.56, 8.25, 1.12, "자동 테스트 기록\n백엔드 500 + 프론트엔드 332 · 2026-09-08 실행", 20)
footnote(s, "인프라: Docker 단계별 빌드로 화면·서버를 통합하며, Docker Compose로 앱과 MySQL 8을 구성합니다.")

# 21. 현재 상태. “완료”는 운영 검증 완료라는 뜻이 아니다.
s = new_slide("구현된 범위와 남은 확인", "소개한 기능은 코드와 테스트가 있으며, 실제 운영 환경에서의 확인이 남아 있습니다.", STATUS)
text(s, .78, 2.10, 5.53, .55, "구현된 업무", 25, BLUE, True)
text(s, .78, 2.94, 5.39, 2.68,
     "연차·복리후생 신청과 결재\n일정·조직·정책 관리\n매일 새벽 5개 자동 처리\n이메일 양식·발송·이력 관리\n퇴직자 파기와 재입사", 21)
text(s, 7.08, 2.10, 5.49, .55, "운영 전 확인", 25, GOLD, True)
text(s, 7.08, 2.94, 5.43, 2.78,
     "회사 Google 계정으로 로그인해 실제 사원 데이터로 확인\n실제 메일 수신과 외부 연동(SMTP · 공휴일 API) 확인\n운영 DB 스키마 반영과 배포 검증", 20)



def validate_deck():
    """기본 구조 검사. 시각 검수는 PowerPoint PNG를 별도로 확인한다."""
    assert 15 <= len(prs.slides) <= 25
    assert len(prs.slides) == 20
    minimum_font = 100.0
    for index, slide in enumerate(prs.slides, 1):
        for shape in slide.shapes:
            assert shape.left >= -10 and shape.top >= -10, (index, shape.name)
            assert shape.left + shape.width <= prs.slide_width + 10, (index, shape.name)
            assert shape.top + shape.height <= prs.slide_height + 10, (index, shape.name)
            frames = []
            if shape.has_text_frame:
                frames.append(shape.text_frame)
            if shape.has_table:
                frames.extend(cell.text_frame for row in shape.table.rows for cell in row.cells)
            for frame in frames:
                for p in frame.paragraphs:
                    for r in p.runs:
                        if r.text and r.font.size:
                            minimum_font = min(minimum_font, r.font.size.pt)
                            assert r.font.size.pt >= 11, (index, r.text)
    assert all(any(sh.has_table for sh in prs.slides[n - 1].shapes) for n in (3,4,7,17,18))
    return {"장수": len(prs.slides), "최소글꼴pt": minimum_font,
            "네이티브표장": [3,4,7,17,18], "흐름도장": list(range(8,17))}


def export_png(pptx_path: Path, out_dir: Path):
    """열린 사용자 프레젠테이션을 건드리지 않고 이 파일만 내보낸다."""
    out_dir.mkdir(parents=True, exist_ok=True)
    ps_path = str(pptx_path.resolve()).replace("'", "''")
    ps_dir = str(out_dir.resolve()).replace("'", "''")
    command = f"""
$ErrorActionPreference = 'Stop'
$pptApp = New-Object -ComObject PowerPoint.Application
$presentation = $null
try {{
    $presentation = $pptApp.Presentations.Open('{ps_path}', $true, $false, $false)
    $presentation.Export('{ps_dir}', 'png', 1600, 900)
}} finally {{
    if ($null -ne $presentation) {{ $presentation.Close() }}
    if ($pptApp.Presentations.Count -eq 0) {{ $pptApp.Quit() }}
    [void][Runtime.InteropServices.Marshal]::ReleaseComObject($pptApp)
}}
"""
    subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-Command", command], check=True)


def main():
    default = Path(__file__).resolve().with_name("연차ON-프로젝트-소개-2026-09-11.pptx")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=default)
    parser.add_argument("--export-png", type=Path)
    args = parser.parse_args()
    result = validate_deck()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    prs.save(args.output)
    result["산출물"] = str(args.output.resolve())
    if args.export_png:
        export_png(args.output, args.export_png)
        result["PNG검수폴더"] = str(args.export_png.resolve())
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
