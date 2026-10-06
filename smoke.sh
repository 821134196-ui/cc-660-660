#!/usr/bin/env bash
# 端到端冒烟：复检退回 / 旧码领用拦截（对运行中的 8080 服务执行）
set -uo pipefail
BASE=http://localhost:8080
PASS=0; FAIL=0
chk() { # chk "描述" 预期状态码 实际状态码 [grep 内容] [响应体]
  local desc="$1" expect="$2" actual="$3"
  local contain="${4:-}" body="${5:-}"
  if [ "$actual" == "$expect" ] && { [ -z "$contain" ] || echo "$body" | grep -q "$contain"; }; then
    echo "✅ $desc [$actual]"; PASS=$((PASS+1))
  else
    echo "❌ $desc（期望 $expect 实际 $actual${contain:+，需含「$contain」}）"; echo "   $body" | head -c 300; echo; FAIL=$((FAIL+1))
  fi
}
req() { # method path json -> 输出 "状态码\n响应体"
  local m="$1" p="$2" data="$3"
  if [ -n "$data" ]; then
    curl -s -w '\n%{http_code}' -X "$m" -H 'Content-Type: application/json' -d "$data" "$BASE$p"
  else
    curl -s -w '\n%{http_code}' -X "$m" "$BASE$p"
  fi
}
split() { echo "$1" | sed '$d'; }
codeof() { echo "$1" | tail -1; }

echo "========== A. 复检退回链路 =========="
C="SMOKE-RE-$(date +%H%M%S)"
r=$(req POST /api/helmets "{\"code\":\"$C\",\"batch\":\"B1\",\"manufactureDate\":\"2025-01-10\"}")
chk "登记新帽 $C" 200 "$(codeof "$r")"
r=$(req POST /api/helmets/$C/impacts '{"severity":"LIGHT","holderName":"测试工人甲","location":"车间"}')
chk "撞击上报→冻结" 200 "$(codeof "$r")" '"severity":"LIGHT"' "$(split "$r")"
r=$(req GET /api/helmets?code=$C "")
chk "扫码状态 FROZEN" 200 "$(codeof "$r")" '"status":"FROZEN"' "$(split "$r")"
r=$(req POST /api/helmets/$C/checkout '{"workerName":"违规发放"}')
chk "冻结帽领用被拦" 409 "$(codeof "$r")" '冻结' "$(split "$r")"
r=$(req POST /api/helmets/$C/inspections '{"shellCondition":"WORN","linerCondition":"AGED","result":"REINSPECT","basis":"吸能性待复测","inspectorName":"赵"}')
chk "安全员退回复检" 200 "$(codeof "$r")" '"result":"REINSPECT"' "$(split "$r")"
r=$(req GET /api/helmets?code=$C "")
chk "复检中状态 REINSPECTING" 200 "$(codeof "$r")" '"status":"REINSPECTING"' "$(split "$r")"
r=$(req POST /api/helmets/$C/checkout '{"workerName":"复检期领用"}')
chk "复检期间领用仍被拦" 409 "$(codeof "$r")" '复检' "$(split "$r")"
# 复检期间漏填依据
r=$(req POST /api/helmets/$C/inspections '{"shellCondition":"INTACT","linerCondition":"INTACT","result":"REUSE","inspectorName":"赵"}')
chk "复用缺依据/下次检查日→400" 400 "$(codeof "$r")"
r=$(req POST /api/helmets/$C/inspections "{\"shellCondition\":\"INTACT\",\"linerCondition\":\"INTACT\",\"result\":\"REUSE\",\"basis\":\"复测合格 GB2811\",\"inspectorName\":\"赵\",\"nextInspectionDate\":\"$(date -d '+6 month' +%F)\",\"attachments\":[{\"fileName\":\"a.txt\",\"contentType\":\"text/plain\",\"contentBase64\":\"$(echo -n '复测附件OK' | base64 -w0)\"}]}")
chk "二次检查复用+附件" 200 "$(codeof "$r")" '"result":"REUSE"' "$(split "$r")"
r=$(req GET /api/helmets?code=$C "")
chk "恢复在役 canCheckout=true" 200 "$(codeof "$r")" '"canCheckout":true' "$(split "$r")"
r=$(req POST /api/helmets/$C/checkout '{"workerName":"正式领用人","team":"一班"}')
chk "复用后可正常领出" 200 "$(codeof "$r")" '"open":true' "$(split "$r")"

echo "========== B. 旧码领用 / 退役不可逆 =========="
D="SMOKE-RT-$(date +%H%M%S)"
req POST /api/helmets "{\"code\":\"$D\",\"batch\":\"B2\",\"manufactureDate\":\"2019-01-01\"}" >/dev/null
req POST /api/helmets/$D/impacts '{"severity":"HEAVY","holderName":"测试工人乙"}' >/dev/null
r=$(req POST /api/helmets/$D/inspections '{"shellCondition":"CRACKED","linerCondition":"DAMAGED","result":"RETIRE","basis":"裂纹+超年限判废","inspectorName":"孙"}')
chk "判定退役" 200 "$(codeof "$r")" '"result":"RETIRE"' "$(split "$r")"
r=$(req GET /api/helmets?code=$D "")
chk "扫码状态 RETIRED 且拦截" 200 "$(codeof "$r")" '"canCheckout":false' "$(split "$r")"
r=$(req POST /api/helmets/$D/checkout '{"workerName":"旧码领用"}')
chk "旧码领用被拦" 409 "$(codeof "$r")" '退役' "$(split "$r")"
r=$(req PUT /api/helmets/$D '{"batch":"B-NEW","manufactureDate":"2025-01-01"}')
chk "改库存不改变退役状态" 200 "$(codeof "$r")" '"status":"RETIRED"' "$(split "$r")"
r=$(req PUT /api/helmets/$D '{"batch":"B-HACK","status":"IN_SERVICE"}')
chk "夹带 status 字段→400" 400 "$(codeof "$r")"
r=$(req POST /api/helmets "{\"code\":\"$D\",\"batch\":\"B3\",\"manufactureDate\":\"2025-01-01\"}")
chk "旧编号重新登记被拦" 409 "$(codeof "$r")" '退役' "$(split "$r")"
r=$(req POST /api/helmets/$D/inspections '{"shellCondition":"INTACT","linerCondition":"INTACT","result":"REUSE","basis":"翻案","inspectorName":"李","nextInspectionDate":"2027-01-01"}')
chk "退役后检查翻案被拦" 409 "$(codeof "$r")"

echo "========== C. 事故追溯 & 未知程度 =========="
E="SMOKE-UN-$(date +%H%M%S)"
req POST /api/helmets "{\"code\":\"$E\",\"batch\":\"B4\",\"manufactureDate\":\"2025-03-01\"}" >/dev/null
r=$(req POST /api/helmets/$E/impacts '{"holderName":"钱多多","location":"3号高炉"}')
chk "severity 缺省→UNKNOWN 且冻结" 200 "$(codeof "$r")" '"severity":"UNKNOWN"' "$(split "$r")"
r=$(req GET /api/helmets/$E "")
chk "事故记录可查当时使用人" 200 "$(codeof "$r")" '"holderName":"钱多多"' "$(split "$r")"
r=$(req POST /api/helmets/$E/impacts '{"severity":"LIGHT"}')
chk "不填使用人→400" 400 "$(codeof "$r")"

echo
echo "结果：PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
