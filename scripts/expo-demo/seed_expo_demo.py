# -*- coding: utf-8 -*-
"""把《六大协同展会演示》资产写入 hjjmate（幂等、可重复执行）。

用法：
    python scripts/expo-demo/seed_expo_demo.py             # 写入
    python scripts/expo-demo/seed_expo_demo.py --dry-run   # 只打印计划，不写入

默认后端 http://127.0.0.1:18088，账号 admin/admin123。
可用 --base-url / --username / --password 覆盖。

顺序：知识库 → 技能 → 数字专家（含技能/知识库绑定）→ 团队 → 工作流（建 → 存草稿 → 编译 → 发布）→ 触发器。
同名资源自动跳过（幂等），已存在的不同名资源不会被修改。
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

import requests

sys.path.insert(0, str(Path(__file__).resolve().parent))
from expo_data import (  # noqa: E402
    AGENTS,
    EXISTING_KB_NAME,
    KBS,
    SKILLS,
    TEAMS,
    TRIGGERS,
    WORKFLOWS,
)

DEFAULT_BASE = "http://127.0.0.1:18088/api/v1"


class Client:
    def __init__(self, base_url: str, username: str, password: str, dry_run: bool = False):
        self.base = base_url.rstrip("/")
        self.dry = dry_run
        self.session = requests.Session()
        self.token = None
        if not dry_run:
            self.login(username, password)

    # ---------- low level ----------
    def login(self, username: str, password: str) -> None:
        r = self.session.post(
            f"{self.base}/auth/login",
            json={"username": username, "password": password},
            timeout=30,
        )
        r.raise_for_status()
        body = r.json()
        if body.get("code") != 200:
            raise RuntimeError(f"登录失败: {body}")
        self.token = body["data"]["token"]
        self.session.headers.update({
            "Authorization": f"Bearer {self.token}",
            # 工作流/触发器接口按请求头解析工作空间，缺失会 500。
            "X-Workspace-Id": "1",
        })

    def get(self, path, **kw):
        return self.session.get(f"{self.base}{path}", timeout=60, **kw)

    def post(self, path, **kw):
        if self.dry:
            return None
        return self.session.post(f"{self.base}{path}", timeout=120, **kw)

    def put(self, path, **kw):
        if self.dry:
            return None
        return self.session.put(f"{self.base}{path}", timeout=120, **kw)

    @staticmethod
    def ok(resp) -> dict:
        if resp is None:
            return {}
        body = resp.json()
        if body.get("code") != 200:
            raise RuntimeError(f"请求失败 {resp.request.method} {resp.request.url}: {body}")
        return body.get("data") or {}


def log(msg: str) -> None:
    print(msg, flush=True)


# ============================================================
# 知识库
# ============================================================
def seed_knowledge_bases(c: Client) -> dict:
    log("\n=== 知识库（LLM Wiki） ===")
    existing = {}
    if not c.dry:
        data = c.ok(c.get("/wiki/knowledge-bases")) or []
        for kb in data:
            existing[kb["name"]] = str(kb["id"])
    else:
        existing[EXISTING_KB_NAME] = "dry-run-existing"

    result = {}
    for spec in KBS:
        name = spec["name"]
        if name in existing:
            log(f"  · 已存在，跳过: {name}")
            result[name] = existing[name]
            continue
        created = c.ok(c.post("/wiki/knowledge-bases", json={"name": name, "description": spec["description"]}))
        kb_id = str(created.get("id")) if created else "dry"
        result[name] = kb_id
        log(f"  + 创建知识库: {name} (id={kb_id})")
        for doc in spec.get("docs", []):
            c.ok(c.post(f"/wiki/knowledge-bases/{kb_id}/raw/text", json=doc))
            log(f"      · 入库文档: {doc['title']}（异步生成 Wiki 页面）")
    result[EXISTING_KB_NAME] = existing.get(EXISTING_KB_NAME, "")
    return result


# ============================================================
# 技能
# ============================================================
def seed_skills(c: Client) -> dict:
    log("\n=== 技能（SKILL.md） ===")
    existing = {}
    if not c.dry:
        page = c.ok(c.get("/skills", params={"page": 1, "size": 500})) or {}
        for s in page.get("records", page if isinstance(page, list) else []) or []:
            if s.get("name"):
                existing[s["name"]] = str(s["id"])
    result = {}
    for spec in SKILLS:
        name = spec["name"]
        if name in existing:
            log(f"  · 已存在，跳过: {spec['nameZh']}")
            result[name] = existing[name]
            continue
        from expo_data import _skill_md  # local import to keep module import light

        content = _skill_md(
            name=spec["name"],
            zh=spec["nameZh"],
            desc=spec["description"],
            version="1.0.0",
            author="expo-demo-seed",
            tags=spec["tags"],
            params=[],
            tools=[],
            body=spec["body"],
        )
        payload = {
            "name": name,
            "nameZh": spec["nameZh"],
            "nameEn": spec["nameZh"],
            "description": spec["description"],
            "skillType": "custom",
            "icon": spec.get("icon", "🧩"),
            "version": "1.0.0",
            "author": "expo-demo-seed",
            "tags": ",".join(spec["tags"]),
            "skillContent": content,
            "enabled": True,
        }
        created = c.ok(c.post("/skills", json=payload))
        sid = str(created.get("id")) if created else "dry"
        result[name] = sid
        log(f"  + 创建技能: {spec['nameZh']} (id={sid})")
    return result


# ============================================================
# 数字专家
# ============================================================
def seed_agents(c: Client, kb_ids: dict, skill_ids: dict) -> dict:
    log("\n=== 数字专家 ===")
    existing = {}
    if not c.dry:
        for a in c.ok(c.get("/agents")) or []:
            existing[a["name"]] = str(a["id"])
    result = {}
    for spec in AGENTS:
        name = spec["name"]
        if name in existing:
            log(f"  · 已存在，跳过: {name}")
            result[name] = existing[name]
            continue
        payload = {
            "name": name,
            "description": spec["description"],
            "agentType": spec["agentType"],
            "systemPrompt": spec["systemPrompt"],
            "icon": spec["icon"],
            "tags": ",".join(spec["tags"]),
            "enabled": True,
            "maxIterations": 12,
        }
        kb_id = kb_ids.get(spec.get("kb"))
        if kb_id:
            payload["primaryKbId"] = kb_id
        created = c.ok(c.post("/agents", json=payload))
        aid = str(created.get("id")) if created else "dry"
        result[name] = aid
        log(f"  + 创建专家: {name} (id={aid})")

        if not c.dry and aid != "dry":
            sids = [int(skill_ids[k]) for k in spec.get("skills", []) if skill_ids.get(k) and skill_ids[k] != "dry"]
            if sids:
                c.ok(c.put(f"/agents/{aid}/skills", json=sids))
                log(f"      · 绑定技能 {len(sids)} 个")
            if kb_id:
                c.ok(c.put(f"/agents/{aid}/kbs", json=[int(kb_id)]))
                log("      · 绑定知识库")
    return result


# ============================================================
# 团队
# ============================================================
def seed_teams(c: Client, agent_ids: dict) -> None:
    log("\n=== 数字团队 ===")
    existing = set()
    if not c.dry:
        for row in c.ok(c.get("/teams")) or []:
            team = row.get("team", row)
            existing.add(team.get("name"))
    for spec in TEAMS:
        if spec["name"] in existing:
            log(f"  · 已存在，跳过: {spec['name']}")
            continue
        lead = agent_ids.get(spec["lead"])
        members = [agent_ids[m] for m in spec["members"] if agent_ids.get(m)]
        if not lead:
            log(f"  ! 跳过（Lead 缺失）: {spec['name']}")
            continue
        payload = {
            "name": spec["name"],
            "description": spec["description"],
            "leadAgentId": lead,
            "memberAgentIds": members,
        }
        created = c.ok(c.post("/teams", json=payload))
        log(f"  + 创建团队: {spec['name']} (Lead={spec['lead']}, 成员 {len(members)} 人)")


# ============================================================
# 工作流
# ============================================================
def seed_workflows(c: Client, agent_ids: dict) -> dict:
    log("\n=== 工作流 ===")
    existing = {}
    if not c.dry:
        for w in c.ok(c.get("/workflows", params={"workspaceId": 1})) or []:
            existing[w["name"]] = w
    result = {}
    for spec in WORKFLOWS:
        name = spec["name"]
        draft = json.dumps(spec["graph"], ensure_ascii=False, indent=2)
        if name in existing:
            wf = existing[name]
            log(f"  · 已存在，更新草稿并重新编译: {name}")
            wid = wf["id"]
            if not c.dry:
                c.ok(c.put(f"/workflows/{wid}/draft", json={"draftJson": draft}))
        else:
            created = c.ok(c.post("/workflows", json={
                "workspaceId": 1,
                "name": name,
                "description": spec["description"],
                "enabled": True,
            }))
            wid = created.get("id")
            log(f"  + 创建工作流: {name} (id={wid})")
            if not c.dry:
                c.ok(c.put(f"/workflows/{wid}/draft", json={"draftJson": draft}))
        if c.dry:
            result[name] = "dry"
            continue
        # 编译
        comp = c.post(f"/workflows/{wid}/compile")
        try:
            body = comp.json() if comp is not None else {"code": 200}
        except Exception:
            body = {"code": 200}
        if body.get("code") != 200:
            log(f"      ! 编译未通过: {json.dumps(body, ensure_ascii=False)[:400]}")
            result[name] = wid
            continue
        log("      · 编译通过")
        c.ok(c.post(f"/workflows/{wid}/publish", json={"note": "展会演示资产初始化"}))
        log("      · 已发布")
        result[name] = wid
    return result


# ============================================================
# 触发器
# ============================================================
def seed_triggers(c: Client, workflow_ids: dict) -> None:
    log("\n=== 触发器 ===")
    existing = set()
    if not c.dry:
        for t in c.ok(c.get("/triggers", params={"workspaceId": 1})) or []:
            existing.add(t.get("name"))
    for spec in TRIGGERS:
        if spec["name"] in existing:
            log(f"  · 已存在，跳过: {spec['name']}")
            continue
        target = workflow_ids.get(spec["targetWorkflow"])
        if not target:
            log(f"  ! 跳过（目标工作流缺失）: {spec['name']}")
            continue
        payload = {
            "workspaceId": 1,
            "name": spec["name"],
            "patternType": spec["patternType"],
            "patternJson": json.dumps(spec["patternJson"], ensure_ascii=False),
            "targetType": spec["targetType"],
            "targetId": int(target) if str(target).isdigit() else target,
            "payloadTemplate": spec["payloadTemplate"],
            "rateLimitPerMin": spec["rateLimitPerMin"],
            "dedupWindowSecs": spec["dedupWindowSecs"],
            "botSelfFilter": spec["botSelfFilter"],
            "enabled": spec["enabled"],
        }
        c.ok(c.post("/triggers", json=payload))
        log(f"  + 创建触发器: {spec['name']} → {spec['targetWorkflow']}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default=DEFAULT_BASE)
    ap.add_argument("--username", default="admin")
    ap.add_argument("--password", default="admin123")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    started = time.time()
    c = Client(args.base_url, args.username, args.password, args.dry_run)
    log(f"目标: {args.base_url}  dry-run={args.dry_run}")

    kb_ids = seed_knowledge_bases(c)
    skill_ids = seed_skills(c)
    agent_ids = seed_agents(c, kb_ids, skill_ids)
    seed_teams(c, agent_ids)
    wf_ids = seed_workflows(c, agent_ids)
    seed_triggers(c, wf_ids)

    log(f"\n完成，用时 {time.time() - started:.1f}s")
    log(f"专家 {len(AGENTS)} / 团队 {len(TEAMS)} / 技能 {len(SKILLS)} / 知识库 {len(KBS) + 1} / 工作流 {len(WORKFLOWS)} / 触发器 {len(TRIGGERS)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
