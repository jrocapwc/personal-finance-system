"""Regression guards for portable collections without committed credentials."""
from pathlib import Path
import json
import re
import pytest

ROOT = Path(__file__).resolve().parents[1]
GROUPS = ["Authentication", "Categories", "Transactions", "Budgets", "Dashboard", "Financial Insights",
          "General Chat", "Finance Chat", "Conversations", "Mock Transaction Imports", "Negative and Security Tests"]


@pytest.mark.parametrize("group", GROUPS)
def test_core_collection_has_requested_groups(group):
    directory = ROOT / "core-api/bruno" / group
    assert (directory / "folder.bru").is_file()
    assert any(path.name != "folder.bru" for path in directory.glob("*.bru"))


@pytest.mark.parametrize("service", ["core-api", "ai-service", "integration-service"])
def test_requests_use_environment_urls_and_no_literal_credentials(service):
    for path in (ROOT / service / "bruno").rglob("*.bru"):
        if "environments" in path.parts or path.name == "folder.bru":
            continue
        content = path.read_text(encoding="utf-8")
        assert "url: {{baseUrl}}/" in content, str(path.relative_to(ROOT))
        assert "vars:pre-request" not in content, str(path.relative_to(ROOT))
        assert not re.search(r'(?mi)^\s*(?:password|email|internalApiKey|accessToken):\s*(?!\{\{)\S+', content), str(path.relative_to(ROOT))
        assert not re.search(r'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', content), str(path.relative_to(ROOT))
        for password in re.findall(r'"password"\s*:\s*"([^"]*)"', content):
            assert password.startswith("{{"), str(path.relative_to(ROOT))
        assert content.count("{") == content.count("}"), str(path.relative_to(ROOT))


def test_public_financial_requests_use_discovered_owned_category_ids():
    for folder in ["Transactions", "Budgets"]:
        for path in (ROOT / "core-api/bruno" / folder).glob("*.bru"):
            assert not re.search(r'"categoryId"\s*:\s*\d+', path.read_text()), str(path.relative_to(ROOT))


def test_json_bodies_are_valid_and_positive_category_contract_has_no_server_fields():
    for service in ["core-api", "ai-service", "integration-service"]:
        for path in (ROOT / service / "bruno").rglob("*.bru"):
            content = path.read_text(encoding="utf-8")
            match = re.search(r'(?ms)^body:json\s*\{\n(.*?)^\}', content)
            if not match:
                continue
            body = re.sub(r'"\{\{[^}]+\}\}"', '"fictional"', match[1])
            body = re.sub(r'\{\{[^}]+\}\}', '1', body)
            try:
                decoded = json.loads(body)
            except json.JSONDecodeError:
                pytest.fail("Invalid JSON body in " + str(path.relative_to(ROOT)))
            if path.parent.name == "Categories" and isinstance(decoded, dict):
                assert set(decoded) <= {"name", "type", "color"}, str(path.relative_to(ROOT))
