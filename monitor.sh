#!/bin/bash

# ==========================================
# 实验2：文件状态监测
# ==========================================

# (1) 清屏
clear

# (2) 提示用户输入要检测其状态的文件名
echo -n "Input file name: "
read -r filename

# 检查文件是否存在。如果不存在，报错并退出
if [ ! -f "$filename" ]; then
    echo "Can't find the file [$filename]!"
    exit 1
fi

# (3) 显示该文件的状态信息
echo "Current status of [$filename] is:"
echo "------"
ls -l "$filename"

# (4) 用 awk 命令截取状态信息中文件的大小并保存
# ls -l 输出的第5列是文件大小（以字节为单位）
current_size=$(ls -l "$filename" | awk '{print $5}')

# 初始化计数器
unchanged_count=0   # 连续未改变的检测次数
change_count=0      # 文件大小改变的累计次数

# (8) 程序循环执行5~7步的操作
while true; do

    # (5) 每隔5秒钟检测一次该文件大小的信息
    sleep 5

    # 再次截取当前最新的文件大小
    new_size=$(ls -l "$filename" | awk '{print $5}')

    # 比较新旧文件大小
    if [ "$new_size" != "$current_size" ]; then
        # (7) 如果文件大小已改变，则保存新的文件大小，并在屏幕上显示提示
        echo "file [$filename] size changed!"

        # 保存新的文件大小作为下一轮的基准
        current_size=$new_size

        # 累计改变次数加1，连续未改变次数重置为0
        change_count=$((change_count + 1))
        unchanged_count=0

        # 检查是否累计改变了两次大小
        if [ $change_count -ge 2 ]; then
            echo "Change number exceed two, test end!"
            sleep 2
            clear
            exit 0
        fi

    else
        # (6) 如果文件大小未改变，则屏幕显示不变，并继续每隔5秒钟检测一次
        echo "test file's status"

        # 连续未改变次数加1
        unchanged_count=$((unchanged_count + 1))

        # 检查是否已连续被检测了十次还未改变大小
        if [ $unchanged_count -ge 10 ]; then
            echo "test number exceed ten!"
            sleep 2
            clear
            exit 0
        fi
    fi

done
