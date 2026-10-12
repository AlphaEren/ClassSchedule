# 生成 8 个识别测试页面（课表布局各不相同的样例）
$dir = $PSScriptRoot + "\cases"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$days='一二三四五六日'

# a) 正方/URP 经典 #kbtable：<td> 里 名称<br>教师<br>周次<br>教室
$sb=New-Object System.Text.StringBuilder
[void]$sb.Append('<table id="kbtable" border="1" cellspacing="0" style="border-collapse:collapse;width:900px;font-size:12px">')
[void]$sb.Append('<tr><th style="width:60px">节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>')
$courses=@(
 @{r=1;c=2;t='高等数学A<br>张三<br>1-16周<br>1-101'},@{r=1;c=3;t='大学英语(3)<br>李四<br>1-16周<br>2-203'},
 @{r=2;c=4;t='线性代数<br>王五<br>1-8周<br>3-305'},@{r=3;c=2;t='大学物理<br>赵六<br>1-16周<br>4-102'},
 @{r=3;c=5;t='程序设计基础<br>孙七<br>1-16周<br>机房A'},@{r=4;c=3;t='体育(1)<br>周八<br>1-16周<br>体育馆'},
 @{r=5;c=6;t='思想道德与法治<br>吴九<br>1-16周<br>5-201'},@{r=6;c=2;t='高等数学A<br>张三<br>1-16周<br>1-101'},
 @{r=7;c=4;t='大学英语(3)<br>李四<br>1-16周<br>2-203'},@{r=8;c=3;t='线性代数<br>王五<br>9-16周<br>3-305'},
 @{r=9;c=5;t='大学物理<br>赵六<br>1-16周<br>4-102'},@{r=10;c=2;t='程序设计基础<br>孙七<br>1-16周<br>机房A'}
)
for($r=1;$r -le 12;$r++){
  [void]$sb.Append("<tr><td>第$($r)节</td>")
  for($c=2;$c -le 8;$c++){
    $hit=$courses | Where-Object { $_.r -eq $r -and $_.c -eq $c }
    if($hit){ [void]$sb.Append("<td>$($hit.t)</td>") } else { [void]$sb.Append('<td></td>') }
  }
  [void]$sb.Append('</tr>')
}
[void]$sb.Append('</table>')
[System.IO.File]::WriteAllText("$dir\a_kbtable.html",$sb.ToString(),(New-Object System.Text.UTF8Encoding($false)))

# b) 卡片式课表（示例大学截图那种）：div 网格 + 卡片里多行文字，含跨两行/空周次行
$sb=New-Object System.Text.StringBuilder
[void]$sb.Append('<div style="width:940px;font-size:12px">')
[void]$sb.Append('<div style="text-align:center;font-size:14px">示例大学</div>')
[void]$sb.Append('<div style="display:grid;grid-template-columns:56px repeat(7,120px);grid-auto-rows:64px;gap:2px">')
[void]$sb.Append('<div style="grid-column:1;grid-row:1">节次</div>')
for($i=1;$i -le 7;$i++){ [void]$sb.Append("<div style=`"grid-column:$($i+1);grid-row:1`">周$($days[$i-1])</div>") }
for($p=1;$p -le 10;$p++){ [void]$sb.Append("<div style=`"grid-column:1;grid-row:$($p+1)`">$([char](0x245f+$p))</div>") }
function Box($col,$row,$span,$inner,$bg){
  $sp=''
  if($span -gt 1){ $sp=" / span $span" }
  "<div style=`"grid-column:$col;grid-row:$row$sp;background:$bg;border:1px solid #888;padding:2px`">$inner</div>"
}
$boxes=@()
$boxes+=(Box 2 2 1 '<div>概率论</div><div>老师F</div><div>1-6周</div><div>B楼</div><div>大教室8</div>' '#dbe9ff')
$boxes+=(Box 4 2 1 '<div>大学物理</div><div>王伟</div><div>1-16周</div><div>A楼</div><div>304</div>' '#ffe9db')
$boxes+=(Box 5 2 1 '<div>程序设计基础</div><div>孙七</div><div>1-16周</div><div>B楼</div><div>机房A</div>' '#dff5df')
$boxes+=(Box 8 2 1 '<div>体育(1)</div><div>周八</div><div>1-16周</div><div>体育馆</div>' '#f0def5')
$boxes+=(Box 3 3 1 '<div>金工实习</div><div>赵刚</div><div></div><div>14-16周</div><div>工程训练中心</div>' '#e8e8e8')
$boxes+=(Box 6 3 1 '<div>形式与政策</div><div>吴九</div><div>1-4周</div><div>A楼</div><div>大教室8</div>' '#fff6cf')
$boxes+=(Box 2 5 1 '<div>大学英语(3)</div><div>张敏</div><div>1-16周</div><div>B楼</div><div>大教室3</div>' '#dbe9ff')
$boxes+=(Box 5 5 2 '<div>高等数学A</div><div>刘洋</div><div>1-16周</div><div>A楼</div><div>201</div>' '#ffe9db')
$boxes+=(Box 7 6 1 '<div>线性代数</div><div>陈静</div><div>1-8周</div><div>A楼</div><div>305</div>' '#dff5df')
$boxes+=(Box 3 7 1 '<div>思想道德与法治</div><div>吴九</div><div>1-16周</div><div>A楼</div><div>201</div>' '#f0def5')
$boxes+=(Box 4 8 1 '<div>大学物理实验</div><div>王伟</div><div>2-15周</div><div>实验楼</div><div>B203</div>' '#dff5df')
$boxes+=(Box 2 9 1 '<div>大学英语(3)</div><div>张敏</div><div>1-16周</div><div>B楼</div><div>大教室3</div>' '#e8e8e8')
[void]$sb.Append(($boxes -join ''))
[void]$sb.Append('</div></div>')
[System.IO.File]::WriteAllText("$dir\b_boxes.html",$sb.ToString(),(New-Object System.Text.UTF8Encoding($false)))

# c) jwglxt：<td> 里多个 kbcontent div
$sb=New-Object System.Text.StringBuilder
[void]$sb.Append('<table id="kbgrid" border="1" cellspacing="0" style="border-collapse:collapse;width:900px;font-size:12px">')
[void]$sb.Append('<tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>')
for($r=1;$r -le 12;$r++){
  [void]$sb.Append("<tr><td>$r</td>")
  for($c=2;$c -le 8;$c++){
    if(($r -eq 1 -and $c -eq 2) -or ($r -eq 3 -and $c -eq 4) -or ($r -eq 5 -and $c -eq 6)){
      [void]$sb.Append('<td><div class="kbcontent1">数据结构</div><div class="kbcontent2">李老师</div><div class="kbcontent3">1-16周</div><div class="kbcontent4">3-201</div></td>')
    } else { [void]$sb.Append('<td></td>') }
  }
  [void]$sb.Append('</tr>')
}
[void]$sb.Append('</table>')
[System.IO.File]::WriteAllText("$dir\c_jwglxt.html",$sb.ToString(),(New-Object System.Text.UTF8Encoding($false)))

# d) 完全不是课表（应当明确失败）
[System.IO.File]::WriteAllText("$dir\d_none.html",'<div><h1>教务系统首页</h1><p>欢迎使用</p><a href="#">学生选课</a></div>',(New-Object System.Text.UTF8Encoding($false)))

# e) 节次竖排在一个格子里（①②③④⑤）
$sb=New-Object System.Text.StringBuilder
[void]$sb.Append('<div style="display:grid;grid-template-columns:50px repeat(5,140px);grid-auto-rows:56px;gap:2px;font-size:12px">')
[void]$sb.Append('<div style="grid-column:1;grid-row:1">节次</div>')
for($i=1;$i -le 5;$i++){ [void]$sb.Append("<div style=`"grid-column:$($i+1);grid-row:1`">星期$($days[$i-1])</div>") }
[void]$sb.Append('<div style="grid-column:1;grid-row:2 / span 5">①<br>②<br>③<br>④<br>⑤</div>')
[void]$sb.Append('<div style="grid-column:2;grid-row:2 / span 2;border:1px solid #999">高等数学<br>张三<br>1-16周<br>1-101</div>')
[void]$sb.Append('<div style="grid-column:4;grid-row:3;border:1px solid #999">大学英语<br>李四<br>3-16周<br>2-203</div>')
[void]$sb.Append('<div style="grid-column:3;grid-row:4 / span 2;border:1px solid #999">数据结构<br>王五<br>1-16周<br>3-305</div>')
[void]$sb.Append('<div style="grid-column:6;grid-row:5;border:1px solid #999">体育<br>赵六<br>1-16周<br>体育馆</div>')
[void]$sb.Append('</div>')
[System.IO.File]::WriteAllText("$dir\e_stacked.html",$sb.ToString(),(New-Object System.Text.UTF8Encoding($false)))

# f) 没有星期表头（按列自动编号）
$sb=New-Object System.Text.StringBuilder
[void]$sb.Append('<div style="display:grid;grid-template-columns:46px repeat(5,140px);grid-auto-rows:56px;gap:2px;font-size:12px">')
[void]$sb.Append('<div style="grid-column:1;grid-row:1 / span 6">①<br>②<br>③<br>④<br>⑤<br>⑥</div>')
[void]$sb.Append('<div style="grid-column:2;grid-row:1;border:1px solid #999">概率论<br>老师F<br>1-6周<br>B楼<br>大教室8</div>')
[void]$sb.Append('<div style="grid-column:4;grid-row:1;border:1px solid #999">大学物理<br>王伟<br>1-16周<br>A楼<br>304</div>')
[void]$sb.Append('<div style="grid-column:3;grid-row:3;border:1px solid #999">程序设计基础<br>孙七<br>1-16周<br>机房A</div>')
[void]$sb.Append('<div style="grid-column:5;grid-row:4;border:1px solid #999">线性代数<br>陈静<br>1-8周<br>A楼<br>305</div>')
[void]$sb.Append('<div style="grid-column:6;grid-row:5;border:1px solid #999">体育(1)<br>周八<br>1-16周<br>体育馆</div>')
[void]$sb.Append('</div>')
[System.IO.File]::WriteAllText("$dir\f_nodays.html",$sb.ToString(),(New-Object System.Text.UTF8Encoding($false)))

# g) 一个格子里两门课 + rowspan 跨行
$sb=New-Object System.Text.StringBuilder
[void]$sb.Append('<table border="1" cellspacing="0" style="border-collapse:collapse;width:820px;font-size:12px">')
[void]$sb.Append('<tr><th style="width:50px">节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>')
[void]$sb.Append('<tr><td>第1节</td><td>课程A<br>张三<br>1-8周<br>1-101<br>课程B<br>李四<br>9-16周<br>2-202</td><td rowspan="2">课程C<br>王五<br>1-16周<br>3-303</td><td></td><td></td><td></td><td></td><td></td></tr>')
for($r=2;$r -le 4;$r++){ [void]$sb.Append("<tr><td>第$($r)节</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>") }
[void]$sb.Append('</table>')
[System.IO.File]::WriteAllText("$dir\g_multi.html",$sb.ToString(),(New-Object System.Text.UTF8Encoding($false)))

# h) DOM 里没有课表，只有接口返回 JSON（模拟"点我的课表才去请求"的系统）
$h=@"
<div id="shell">正在加载...</div>
<script>
var KB='{"currentWeek":1,"kbList":['+
 '{"kcmc":"高等数学A","xm":"张三","cdmc":"1-101","xqj":"1","jcs":"0102","zcd":"1-16"},'+
 '{"kcmc":"大学英语(3)","xm":"李四","cdmc":"2-203","xqj":"2","jcs":"0304","zcd":"1-16"},'+
 '{"kcmc":"线性代数","xm":"王五","cdmc":"3-305","xqj":"3","jcs":"0102","zcd":"1-8"},'+
 '{"kcmc":"大学物理","xm":"赵六","cdmc":"4-102","xqj":"4","jcs":"0506","zcd":"1-16"},'+
 '{"kcmc":"程序设计基础","xm":"孙七","cdmc":"机房A","xqj":"5","jcs":"0102","zcd":"1-16"},'+
 '{"kcmc":"体育(1)","xm":"周八","cdmc":"体育馆","xqj":"3","jcs":[7,8],"zcd":[1,6]}'+
 ']}';
var u=URL.createObjectURL(new Blob([KB],{type:'application/json'}));
var x=new XMLHttpRequest();
x.open('GET',u,true);
x.onload=function(){ document.getElementById('shell').textContent='已加载 '+x.responseText.length+' 字节'; };
x.send(null);
</script>
"@
[System.IO.File]::WriteAllText("$dir\h_xhr.html",$h,(New-Object System.Text.UTF8Encoding($false)))
"cases generated in $dir"